package com.planmate.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.util.ConstantsUtil;

/**
 * The Discover feed: upcoming public events, ranked for the viewer.
 *
 * Score = social + popularity + soonness
 * - social (logged-in viewers only): 5 per friend going, +5 when a friend is hosting
 * - popularity: log(1 + people going), so a crowd helps but never drowns out friends
 * - soonness: 3 for events today, 1.5 tomorrow, about 0.4 a week out
 * Ties go to the event that starts first. A tag narrows the candidates; the ranking stays the same.
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

	/** One page of the feed. */
	public static class FeedPage {
		private final List<FeedEvent> events;
		private final int page;
		private final boolean hasMore;

		FeedPage(List<FeedEvent> events, int page, boolean hasMore) {
			this.events = events;
			this.page = page;
			this.hasMore = hasMore;
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

	/** Who is looking at the feed. Guests have no id and no friends. */
	static final class Viewer {
		static final Viewer GUEST = new Viewer(null, null, Collections.emptyMap());

		final String email;
		final String id;
		final Map<String, User> friendsById;

		Viewer(String email, String id, Map<String, User> friendsById) {
			this.email = email;
			this.id = id;
			this.friendsById = friendsById;
		}

		boolean isFriendEmail(String email) {
			return email != null && friendsById.values().stream().anyMatch(f -> email.equals(f.getEmail()));
		}
	}

	private final EventMongoRepository repository;
	private final UserService userService;
	private final FriendshipService friendshipService;

	public FeedService(EventMongoRepository repository, UserService userService,
			FriendshipService friendshipService) {
		this.repository = repository;
		this.userService = userService;
		this.friendshipService = friendshipService;
	}

	public FeedPage getFeed(String viewerEmail, String tag, int page, int size) {
		return getFeed(viewerEmail, tag, page, size, Instant.now());
	}

	FeedPage getFeed(String viewerEmail, String tag, int page, int size, Instant now) {
		int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
		int pageIndex = Math.max(0, page);

		String normalizedTag = normalizeTag(tag);
		Pageable candidates = PageRequest.of(0, MAX_CANDIDATES, Sort.by("startTime").ascending());
		List<EventDoc> events = normalizedTag == null ? repository.findUpcomingPublic(now, candidates)
				: repository.findUpcomingPublicWithTag(now, normalizedTag, candidates);

		Viewer viewer = viewer(viewerEmail);
		List<EventDoc> ranked = events.stream()
				.sorted(Comparator.comparingDouble((EventDoc e) -> -score(e, viewer, now))
						.thenComparing(EventDoc::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(EventDoc::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.collect(Collectors.toList());

		long from = (long) pageIndex * pageSize;
		if (from >= ranked.size()) {
			return new FeedPage(Collections.emptyList(), pageIndex, false);
		}
		int to = (int) Math.min(ranked.size(), from + pageSize);
		Map<String, User> hosts = new HashMap<>();
		List<FeedEvent> cards = ranked.subList((int) from, to).stream().map(e -> toFeedEvent(e, viewer, hosts))
				.collect(Collectors.toList());
		return new FeedPage(cards, pageIndex, to < ranked.size());
	}

	/** One event as a feed card for this viewer, e.g. right after they joined it. */
	public FeedEvent toFeedEvent(EventDoc event, String viewerEmail) {
		return toFeedEvent(event, viewer(viewerEmail), new HashMap<>());
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
		return social + popularity + soonness;
	}

	private FeedEvent toFeedEvent(EventDoc event, Viewer viewer, Map<String, User> hostCache) {
		List<String> attendees = attendeesOf(event);
		String creatorId = event.getCreatorId();

		FeedEvent card = new FeedEvent();
		card.setId(event.getId());
		card.setTitle(event.getTitle());
		card.setDescription(event.getDescription());
		card.setStartTime(event.getStartTime());
		card.setEndTime(event.getEndTime());
		card.setLocation(event.getLocation());
		card.setTags(event.getTags() == null ? Collections.emptyList() : event.getTags());

		boolean hosting = viewer.email != null && viewer.email.equals(creatorId);
		User creator = creatorId == null || creatorId.startsWith("guest_") ? null : lookupHost(creatorId, hostCache);
		card.setHost(new FeedEvent.Host(hostName(creatorId, hostCache), creator == null ? null : creator.getUsername(),
				viewer.isFriendEmail(creatorId), hosting));
		card.setHosting(hosting);

		card.setAttendeeCount(attendees.size());
		card.setGoing(viewer.id != null && attendees.contains(viewer.id));
		// Only the viewer's friends are named; everyone else is just part of the count
		card.setFriendsGoing(attendees.stream().map(viewer.friendsById::get).filter(Objects::nonNull)
				.map(f -> new FeedEvent.Friend(f.getId(), f.getFirstName(), f.getLastName(), f.getUsername()))
				.collect(Collectors.toList()));
		return card;
	}

	private Viewer viewer(String email) {
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
		return new Viewer(email, String.valueOf(me.getId()), friends);
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

	private static List<String> attendeesOf(EventDoc event) {
		return event.getAttendeeIds() == null ? Collections.emptyList() : event.getAttendeeIds();
	}
}
