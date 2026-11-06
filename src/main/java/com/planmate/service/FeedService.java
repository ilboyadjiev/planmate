package com.planmate.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto.UserSettings;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.util.CityTags;
import com.planmate.util.ConstantsUtil;
import com.planmate.util.EventSeries;

/**
 * The Discover feed: upcoming public events, ranked for the viewer.
 *
 * Score = social + popularity + soonness
 * - social (logged-in viewers only): 5 per friend going, +5 when a friend is hosting
 * - popularity: log(1 + people going), so a crowd helps but never drowns out friends
 * - soonness: 3 for events today, 1.5 tomorrow, about 0.4 a week out
 * Ties go to the event that starts first. A tag narrows the candidates; the ranking stays the same.
 *
 * Variety, after scoring:
 * - a show listed once per date (same host, title and venue, see EventSeries) gets one card, its best-placed
 *   date, which says how many more dates there are (moreDates);
 * - an event loses REPEAT_PENALTY for each of the VARIETY_WINDOW cards right before it that share its main topic
 *   (first tag that is not the city tag), so ten concerts in a row become concerts mixed with other things.
 *
 * The event page shows the rest: the show's other dates and similar events (related()).
 *
 * Candidates are the next MAX_CANDIDATES upcoming public events, ranked in memory. That is plenty at the current
 * scale; move the scoring into an aggregation once there are thousands of upcoming public events.
 */
@Service
public class FeedService {

	public static final int MAX_PAGE_SIZE = 50;
	static final int MAX_CANDIDATES = 500;
	static final String UNKNOWN_HOST = "PlanMate user";
	static final String GUEST_HOST = "Guest";
	static final int VARIETY_WINDOW = 4;
	static final double REPEAT_PENALTY = 1.5;
	static final int MAX_MORE_DATES = 30;
	static final int MAX_SIMILAR = 4;

	/** One page of the feed. */
	public static class FeedPage {
		private final List<FeedEvent> events;
		private final int page;
		private final boolean hasMore;
		/** "Around me" only: the city the events are for, or null when the user has no city in their profile. */
		private final String city;
		/** Search only: how many events matched in total. Null (and left out of the JSON) in the feed. */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private Integer total;

		public FeedPage(List<FeedEvent> events, int page, boolean hasMore) {
			this(events, page, hasMore, null);
		}

		FeedPage(List<FeedEvent> events, int page, boolean hasMore, String city) {
			this.events = events;
			this.page = page;
			this.hasMore = hasMore;
			this.city = city;
		}

		public String getCity() {
			return city;
		}

		public Integer getTotal() {
			return total;
		}

		public FeedPage withTotal(int total) {
			this.total = total;
			return this;
		}

		public List<FeedEvent> getEvents() {
			return events;
		}

		public int getPage() {
			return page;
		}

		public boolean isHasMore() {
			return hasMore;
		}
	}

	/** Who is looking at the feed. Guests have no id, no friends and no topic settings. */
	static final class Viewer {
		static final Viewer GUEST = new Viewer(null, null, Collections.emptyMap());

		final String email;
		final String id;
		final Map<String, User> friendsById;
		/** Topics the viewer likes (ranked higher) and hides (left out of "For you" and "Around me"). */
		final Set<String> likedTags;
		final Set<String> hiddenTags;
		/** The city tag of the viewer's profile city ("#munich"), or null. */
		String cityTag;
		/** Per request: whether someone keeps the events they go to to themselves (going visibility only_me). */
		final Map<Long, Boolean> hidesGoing = new HashMap<>();

		Viewer(String email, String id, Map<String, User> friendsById) {
			this(email, id, friendsById, Collections.emptySet(), Collections.emptySet());
		}

		Viewer(String email, String id, Map<String, User> friendsById, Set<String> likedTags, Set<String> hiddenTags) {
			this.email = email;
			this.id = id;
			this.friendsById = friendsById;
			this.likedTags = likedTags;
			this.hiddenTags = hiddenTags;
		}

		boolean hidesTopicOf(EventDoc event) {
			return !hiddenTags.isEmpty() && event.getTags() != null
					&& event.getTags().stream().anyMatch(hiddenTags::contains);
		}

		long likedTopicsOf(EventDoc event) {
			return likedTags.isEmpty() || event.getTags() == null ? 0
					: event.getTags().stream().distinct().filter(likedTags::contains).count();
		}

		boolean isFriendEmail(String email) {
			return email != null && friendsById.values().stream().anyMatch(f -> email.equals(f.getEmail()));
		}
	}

	private final EventMongoRepository repository;
	private final UserService userService;
	private final FriendshipService friendshipService;
	private final FriendGroupService friendGroupService;
	private final UserSettingsService settings;

	/** Without settings: every viewer and person has the default settings (for tests). */
	public FeedService(EventMongoRepository repository, UserService userService,
			FriendshipService friendshipService, FriendGroupService friendGroupService) {
		this(repository, userService, friendshipService, friendGroupService, UserSettingsService.DEFAULTS_ONLY);
	}

	@Autowired
	public FeedService(EventMongoRepository repository, UserService userService,
			FriendshipService friendshipService, FriendGroupService friendGroupService, UserSettingsService settings) {
		this.repository = repository;
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.friendGroupService = friendGroupService;
		this.settings = settings;
	}

	/**
	 * A friend group's upcoming events, soonest first: the ones the viewer created or was invited to.
	 * Only members of the group may read it (404 otherwise, so group ids cannot be probed).
	 */
	public FeedPage getGroupFeed(String viewerEmail, Long groupId, int page, int size) {
		return getGroupFeed(viewerEmail, groupId, page, size, Instant.now());
	}

	FeedPage getGroupFeed(String viewerEmail, Long groupId, int page, int size, Instant now) {
		Viewer viewer = viewer(viewerEmail);
		if (viewer.id == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		FriendGroup group = friendGroupService.groupsOf(viewerEmail).stream()
				.filter(g -> g.getId().equals(groupId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found."));

		int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
		int pageIndex = Math.max(0, page);
		Page<EventDoc> events = repository.findUpcomingForGroupMember(String.valueOf(groupId), now, viewerEmail,
				viewer.id, PageRequest.of(pageIndex, pageSize, Sort.by("startTime").ascending()));

		// Group members share the group, so they may see each other's names on the cards
		Map<String, User> members = group.getMembers().stream()
				.collect(Collectors.toMap(m -> String.valueOf(m.getId()), m -> m, (a, b) -> a));
		Map<String, User> hosts = new HashMap<>();
		List<FeedEvent> cards = events.getContent().stream().map(e -> toFeedEvent(e, viewer, hosts, members))
				.collect(Collectors.toList());
		return new FeedPage(cards, pageIndex, events.hasNext());
	}

	public FeedPage getFeed(String viewerEmail, String tag, int page, int size) {
		return getFeed(viewerEmail, tag, page, size, Instant.now());
	}

	FeedPage getFeed(String viewerEmail, String tag, int page, int size, Instant now) {
		String normalizedTag = normalizeTag(tag);
		Pageable candidates = candidates();
		List<EventDoc> events = normalizedTag == null ? repository.findUpcomingPublic(now, candidates)
				: repository.findUpcomingPublicWithTag(now, normalizedTag, candidates);
		Viewer viewer = viewer(viewerEmail);
		if (normalizedTag == null) {
			events = inViewersCity(events, viewer);
		}
		// Hidden topics stay out of "For you"; choosing a tag chip still shows that tag's events
		return rankedPage(events, viewer, page, size, now, normalizedTag == null);
	}

	/**
	 * "Around me": upcoming public events in the city from the viewer's profile, ranked like "For you".
	 * Without a city in the profile the page is empty and its city is null, so the app can ask for one.
	 */
	public FeedPage getNearbyFeed(String viewerEmail, int page, int size) {
		return getNearbyFeed(viewerEmail, page, size, Instant.now());
	}

	FeedPage getNearbyFeed(String viewerEmail, int page, int size, Instant now) {
		User me = viewerEmail == null ? null : userService.getUserByEmail(viewerEmail);
		if (me == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		String city = me.getContactData() == null ? null : StringUtils.normalizeSpace(me.getContactData().getCity());
		if (StringUtils.isEmpty(city)) {
			return new FeedPage(Collections.emptyList(), Math.max(0, page), false, null);
		}
		String quoted = Pattern.quote(city);
		List<EventDoc> events = repository.findUpcomingPublicInCity(now, "^" + quoted + "$",
				"(^|[^\\p{L}])" + quoted + "($|[^\\p{L}])", cityTag(city), candidates());
		FeedPage ranked = rankedPage(events, viewer(viewerEmail), page, size, now, true);
		return new FeedPage(ranked.getEvents(), ranked.getPage(), ranked.isHasMore(), city);
	}

	/**
	 * "For you" for someone with a city in their profile: events PlanMate discovered in other cities stay out (a
	 * concert in Sofia is no use in Munich). People's own events, and discovered ones without a city (game releases,
	 * online sales), stay in. Guests and people without a city see everything; a city's tag chip shows its events.
	 */
	static List<EventDoc> inViewersCity(List<EventDoc> events, Viewer viewer) {
		if (viewer.cityTag == null) {
			return events;
		}
		return events.stream()
				.filter(e -> e.getDiscoverySource() == null || e.getCityTag() == null || viewer.cityTag.equals(e.getCityTag()))
				.collect(Collectors.toList());
	}

	/** "New York" becomes "#newyork" and "München" becomes "#munich", like automatic event city tags. */
	static String cityTag(String city) {
		return CityTags.forCity(city);
	}

	private static Pageable candidates() {
		return PageRequest.of(0, MAX_CANDIDATES, Sort.by("startTime").ascending());
	}

	private FeedPage rankedPage(List<EventDoc> events, Viewer viewer, int page, int size, Instant now,
			boolean hideTopics) {
		int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
		int pageIndex = Math.max(0, page);

		Map<EventDoc, Double> scores = new java.util.IdentityHashMap<>();
		events.forEach(e -> scores.put(e, score(e, viewer, now)));
		List<EventDoc> byScore = events.stream()
				.filter(e -> !hideTopics || !viewer.hidesTopicOf(e))
				.sorted(Comparator.comparingDouble((EventDoc e) -> -scores.get(e))
						.thenComparing(EventDoc::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(EventDoc::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.collect(Collectors.toList());
		Map<EventDoc, Integer> moreDates = new java.util.IdentityHashMap<>();
		List<EventDoc> ranked = varied(onePerSeries(byScore, moreDates), scores);

		long from = (long) pageIndex * pageSize;
		if (from >= ranked.size()) {
			return new FeedPage(Collections.emptyList(), pageIndex, false);
		}
		int to = (int) Math.min(ranked.size(), from + pageSize);
		Map<String, User> hosts = new HashMap<>();
		List<FeedEvent> cards = ranked.subList((int) from, to).stream()
				.map(e -> withMoreDates(toFeedEvent(e, viewer, hosts, Collections.emptyMap()), moreDates.get(e)))
				.collect(Collectors.toList());
		return new FeedPage(cards, pageIndex, to < ranked.size());
	}

	private static FeedEvent withMoreDates(FeedEvent card, Integer moreDates) {
		card.setMoreDates(moreDates == null || moreDates == 0 ? null : moreDates);
		return card;
	}

	/**
	 * One event per show, keeping the order: the first (best-placed) date stays, the later ones are left out and
	 * counted in moreDates.
	 */
	static List<EventDoc> onePerSeries(List<EventDoc> ranked, Map<EventDoc, Integer> moreDates) {
		Map<String, EventDoc> first = new HashMap<>();
		List<EventDoc> kept = new java.util.ArrayList<>();
		for (EventDoc e : ranked) {
			String series = EventSeries.key(e);
			EventDoc shown = series == null ? null : first.putIfAbsent(series, e);
			if (shown == null) {
				kept.add(e);
			} else {
				moreDates.merge(shown, 1, Integer::sum);
			}
		}
		return kept;
	}

	/**
	 * Picks the cards in order of score, but each card loses REPEAT_PENALTY for every one of the VARIETY_WINDOW
	 * cards right before it with the same main topic. Without topics, or with a lead bigger than the penalty, the
	 * order stays as scored.
	 */
	static List<EventDoc> varied(List<EventDoc> ranked, Map<EventDoc, Double> scores) {
		List<EventDoc> left = new java.util.ArrayList<>(ranked);
		List<EventDoc> picked = new java.util.ArrayList<>(ranked.size());
		while (!left.isEmpty()) {
			List<String> recent = picked.subList(Math.max(0, picked.size() - VARIETY_WINDOW), picked.size()).stream()
					.map(FeedService::mainTopic).collect(Collectors.toList());
			int best = 0;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < left.size(); i++) {
				EventDoc e = left.get(i);
				String topic = mainTopic(e);
				long repeats = topic == null ? 0 : recent.stream().filter(topic::equals).count();
				double s = scores.getOrDefault(e, 0.0) - REPEAT_PENALTY * repeats;
				if (s > bestScore) { // strictly: equal scores keep their order
					best = i;
					bestScore = s;
				}
			}
			picked.add(left.remove(best));
		}
		return picked;
	}

	/** The first tag that is not the city tag ("#concert"), or null. */
	static String mainTopic(EventDoc e) {
		return topicsOf(e).stream().findFirst().orElse(null);
	}

	/** An event's tags without its city tag, in order. */
	static Set<String> topicsOf(EventDoc e) {
		Set<String> topics = new LinkedHashSet<>();
		if (e.getTags() != null) {
			for (String tag : e.getTags()) {
				String t = tag == null ? "" : tag.trim().toLowerCase();
				if (!t.isEmpty() && !t.equals(e.getCityTag())) {
					topics.add(t);
				}
			}
		}
		return topics;
	}

	/**
	 * What the event page shows below an event: the show's other upcoming dates (soonest first) and up to
	 * MAX_SIMILAR similar events. Similar means public and upcoming, sharing a topic, in the same city (or without
	 * one, like online events), not another date of the same show, and not one of the viewer's hidden topics;
	 * more shared topics first, then the feed's own score. Only public events are listed.
	 */
	public RelatedEvents related(EventDoc event, String viewerEmail) {
		return related(event, viewerEmail, Instant.now());
	}

	RelatedEvents related(EventDoc event, String viewerEmail, Instant now) {
		Viewer viewer = viewer(viewerEmail);
		Map<String, User> hosts = new HashMap<>();
		String series = EventSeries.key(event);

		List<FeedEvent> dates = Collections.emptyList();
		if (series != null && event.getCreatorId() != null && Boolean.TRUE.equals(event.getIsPublic())) {
			dates = repository.findUpcomingPublicByCreatorAndTitle(event.getCreatorId(), event.getTitle(), now,
					PageRequest.of(0, MAX_MORE_DATES + 1, Sort.by("startTime").ascending())).stream()
					.filter(e -> !Objects.equals(e.getId(), event.getId()) && series.equals(EventSeries.key(e)))
					.limit(MAX_MORE_DATES)
					.map(e -> toFeedEvent(e, viewer, hosts, Collections.emptyMap()))
					.collect(Collectors.toList());
		}

		Set<String> topics = topicsOf(event);
		List<FeedEvent> similar = Collections.emptyList();
		if (!topics.isEmpty()) {
			Map<EventDoc, Double> scores = new java.util.IdentityHashMap<>();
			for (EventDoc e : repository.findUpcomingPublic(now, candidates())) {
				long shared = topicsOf(e).stream().filter(topics::contains).count();
				boolean sameArea = event.getCityTag() == null || e.getCityTag() == null
						|| event.getCityTag().equals(e.getCityTag());
				if (shared > 0 && sameArea && !Objects.equals(e.getId(), event.getId())
						&& !Objects.equals(series, EventSeries.key(e)) && !viewer.hidesTopicOf(e)) {
					scores.put(e, 3.0 * shared + score(e, viewer, now));
				}
			}
			List<EventDoc> ranked = scores.keySet().stream()
					.sorted(Comparator.comparingDouble((EventDoc e) -> -scores.get(e))
							.thenComparing(EventDoc::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
							.thenComparing(EventDoc::getId, Comparator.nullsLast(Comparator.naturalOrder())))
					.collect(Collectors.toList());
			Map<EventDoc, Integer> moreDates = new java.util.IdentityHashMap<>();
			similar = onePerSeries(ranked, moreDates).stream().limit(MAX_SIMILAR)
					.map(e -> withMoreDates(toFeedEvent(e, viewer, hosts, Collections.emptyMap()), moreDates.get(e)))
					.collect(Collectors.toList());
		}
		return new RelatedEvents(dates, similar);
	}

	/** Below an event on its page: other dates of the same show, and similar events. */
	public static class RelatedEvents {
		private final List<FeedEvent> moreDates;
		private final List<FeedEvent> similar;

		public RelatedEvents(List<FeedEvent> moreDates, List<FeedEvent> similar) {
			this.moreDates = moreDates;
			this.similar = similar;
		}

		public List<FeedEvent> getMoreDates() {
			return moreDates;
		}

		public List<FeedEvent> getSimilar() {
			return similar;
		}
	}

	/** One event as a feed card for this viewer, e.g. right after they joined it. */
	public FeedEvent toFeedEvent(EventDoc event, String viewerEmail) {
		return toFeedEvent(event, viewer(viewerEmail), new HashMap<>(), Collections.emptyMap());
	}

	/**
	 * The name shown for an event's creator: first and last name, else username. Never the email.
	 * hostCache avoids looking up the same creator twice within one request.
	 */
	public String hostName(String creatorId, Map<String, User> hostCache) {
		if (creatorId == null) {
			return UNKNOWN_HOST;
		}
		if (creatorId.startsWith("guest_")) {
			return GUEST_HOST;
		}
		return displayName(lookupHost(creatorId, hostCache));
	}

	static String normalizeTag(String tag) {
		if (StringUtils.isBlank(tag)) {
			return null;
		}
		String normalized = tag.trim().toLowerCase().replace(",", "");
		return normalized.startsWith("#") ? normalized : "#" + normalized;
	}

	static double score(EventDoc event, Viewer viewer, Instant now) {
		List<String> attendees = attendeesOf(event);

		double hoursAway = event.getStartTime() == null ? Double.MAX_VALUE
				: Math.max(0, Duration.between(now, event.getStartTime()).toMinutes() / 60.0);
		double soonness = 3.0 / (1.0 + hoursAway / 24.0);
		double popularity = Math.log1p(attendees.size());

		double social = 0;
		if (viewer.id != null) {
			long friendsGoing = attendees.stream().filter(viewer.friendsById::containsKey).count();
			social = 5.0 * friendsGoing + (viewer.isFriendEmail(event.getCreatorId()) ? 5.0 : 0);
		}
		// Each liked topic counts like a lot of popularity, but less than a friend going (at most two topics)
		double topics = 2.0 * Math.min(2, viewer.likedTopicsOf(event));
		return social + topics + popularity + soonness;
	}

	/**
	 * knownPeople: besides the viewer's friends, people the viewer may see by name on this card (fellow group
	 * members in a group's feed). Everyone else going is only counted.
	 */
	FeedEvent toFeedEvent(EventDoc event, Viewer viewer, Map<String, User> hostCache,
			Map<String, User> knownPeople) {
		String creatorId = event.getCreatorId();
		Map<String, String> responses = event.getResponses() == null ? Collections.emptyMap() : event.getResponses();
		boolean invited = viewer.id != null && event.getParticipantIds() != null
				&& event.getParticipantIds().contains(viewer.id);
		String myResponse = invited ? responses.getOrDefault(viewer.id, "pending") : null;

		Set<String> going = goingIds(event);

		FeedEvent card = new FeedEvent();
		card.setId(event.getId());
		card.setTitle(event.getTitle());
		card.setDescription(event.getDescription());
		card.setStartTime(event.getStartTime());
		card.setEndTime(event.getEndTime());
		card.setLocation(event.getLocation());
		card.setTags(event.getTags() == null ? Collections.emptyList() : event.getTags());
		card.setCancelled(Boolean.TRUE.equals(event.getCancelled()));
		card.setSourceUrl(event.getSourceUrl());

		boolean hosting = viewer.email != null && viewer.email.equals(creatorId);
		User creator = creatorId == null || creatorId.startsWith("guest_") ? null : lookupHost(creatorId, hostCache);
		card.setHost(new FeedEvent.Host(creator == null ? null : creator.getId(), hostName(creatorId, hostCache),
				creator == null ? null : creator.getUsername(), viewer.isFriendEmail(creatorId), hosting,
				creator == null ? null : creator.getAvatarKey()));
		card.setHosting(hosting);

		card.setInvited(invited);
		card.setMyResponse(myResponse);
		card.setAttendeeCount(going.size());
		card.setGoing(viewer.id != null && going.contains(viewer.id));
		// Only the viewer's friends (and fellow group members in a group feed) are named; others are just counted.
		// People who keep the events they go to to themselves are only counted too.
		List<User> named = going.stream().filter(id -> !id.equals(viewer.id))
				.map(id -> viewer.friendsById.containsKey(id) ? viewer.friendsById.get(id) : knownPeople.get(id))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		rememberGoingVisibility(viewer, named);
		card.setFriendsGoing(named.stream()
				.filter(f -> !viewer.hidesGoing.getOrDefault(f.getId(), false))
				.map(f -> new FeedEvent.Friend(f.getId(), f.getFirstName(), f.getLastName(), f.getUsername(),
						f.getAvatarKey()))
				.collect(Collectors.toList()));
		return card;
	}

	/** Looks up, once per request, which of these people keep the events they go to to themselves. */
	void rememberGoingVisibility(Viewer viewer, Collection<User> people) {
		List<Long> unknown = people.stream().map(User::getId)
				.filter(id -> id != null && !viewer.hidesGoing.containsKey(id)).distinct().collect(Collectors.toList());
		if (unknown.isEmpty()) {
			return;
		}
		settings.of(unknown).forEach((id, s) -> viewer.hidesGoing.put(id,
				UserSettings.VISIBLE_ONLY_ME.equals(s.getGoingVisibility())));
	}

	Viewer viewer(String email) {
		if (email == null) {
			return Viewer.GUEST;
		}
		User me = userService.getUserByEmail(email);
		if (me == null || me.getId() == null) {
			return Viewer.GUEST;
		}
		Map<String, User> friends = new HashMap<>();
		for (Friendship friendship : friendshipService.getFriendshipList(email)) {
			if (!ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(friendship.getStatus())) {
				continue;
			}
			User a = friendship.getUserA();
			User other = a != null && email.equals(a.getEmail()) ? friendship.getUserB() : a;
			if (other != null && other.getId() != null) {
				friends.put(String.valueOf(other.getId()), other);
			}
		}
		UserSettings mine = settings.of(me.getId());
		Viewer viewer = new Viewer(email, String.valueOf(me.getId()), friends,
				new HashSet<>(UserSettingsService.likedTags(mine)), new HashSet<>(UserSettingsService.hiddenTags(mine)));
		String city = me.getContactData() == null ? null : StringUtils.normalizeSpace(me.getContactData().getCity());
		viewer.cityTag = StringUtils.isEmpty(city) ? null : cityTag(city);
		return viewer;
	}

	private User lookupHost(String creatorId, Map<String, User> hostCache) {
		return hostCache.computeIfAbsent(creatorId, userService::getUserByEmail);
	}

	private static String displayName(User user) {
		if (user == null) {
			return UNKNOWN_HOST;
		}
		String fullName = StringUtils.normalizeSpace(StringUtils.defaultString(user.getFirstName()) + " "
				+ StringUtils.defaultString(user.getLastName()));
		if (!fullName.isEmpty()) {
			return fullName;
		}
		return StringUtils.isBlank(user.getUsername()) ? UNKNOWN_HOST : user.getUsername();
	}

	/** User ids of everyone going: joined from the feed, or accepted an invitation. */
	static Set<String> goingIds(EventDoc event) {
		Set<String> going = new LinkedHashSet<>(attendeesOf(event));
		if (event.getResponses() != null) {
			event.getResponses().forEach((userId, answer) -> {
				if ("accepted".equals(answer)) {
					going.add(userId);
				}
			});
		}
		return going;
	}

	private static List<String> attendeesOf(EventDoc event) {
		return event.getAttendeeIds() == null ? Collections.emptyList() : event.getAttendeeIds();
	}
}
