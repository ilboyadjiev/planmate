package com.planmate.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.planmate.dto.FriendGroup;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.service.FeedService.FeedPage;
import com.planmate.util.SearchText;

/**
 * Free-text event search, ranked the way a search engine would, plus what PlanMate knows about your friends.
 *
 * 1. Which events: upcoming events the searcher may see (public ones, and private ones they host, were invited
 *    to or joined) that contain a word starting with a typed word, or that a friend with that name hosts or goes
 *    to, or that one of the searcher's groups with that name is invited to. Words are matched on an indexed list
 *    of each event's words (EventDoc.searchTerms), so Mongo only reads events that can match.
 *
 * 2. Relevance, in the spirit of BM25 (the scoring used by Elasticsearch/Lucene), per typed word:
 *    - where it was found: title 4, tags 3, location 2.5, description 1; a friend or group name 3,
 *    - how well: the whole word 1.0, the start of a longer word 0.7 ("conc" in "concert"),
 *    - how often, with diminishing returns: tf * (k + 1) / (tf + k), k = 1.2,
 *    - how rare the word is among the matching events (IDF): rare words count more than common ones.
 *    The best field counts in full, other fields a quarter. Events containing all typed words come first: if any
 *    event matches every word, events that miss one are left out; otherwise fewer matched words score less.
 *    All typed words in a row in the title ("jazz night") add a bonus.
 *
 * 3. Personal boost, multiplied in so it reorders similarly relevant events but cannot lift an irrelevant one:
 *    friends going (+15% each, up to four), a friend hosting (+20%), your own event (+25%), and starting soon.
 *
 * Ranking happens in memory over at most MAX_CANDIDATES events, soonest first. That is plenty for now; with many
 * thousands of upcoming events, move to Atlas Search or Elasticsearch, which also tolerate typos.
 */
@Service
public class EventSearchService {

	private static final Logger LOG = LoggerFactory.getLogger(EventSearchService.class);

	public static final int MAX_PAGE_SIZE = 50;
	static final int MAX_CANDIDATES = 500;
	static final int MAX_QUERY_LENGTH = 100;
	static final int MAX_QUERY_WORDS = 8;

	static final double TITLE = 4.0;
	static final double TAGS = 3.0;
	static final double PLACE = 2.5;
	static final double DESCRIPTION = 1.0;
	static final double PERSON = 3.0;
	static final double GROUP = 3.0;
	static final double WHOLE_WORD = 1.0;
	static final double WORD_START = 0.7;
	static final double K1 = 1.2;
	static final double OTHER_FIELDS = 0.25;
	static final double PHRASE_BONUS = 2.0;

	private final MongoTemplate mongo;
	private final FeedService feedService;
	private final FriendGroupService friendGroupService;

	public EventSearchService(MongoTemplate mongo, FeedService feedService, FriendGroupService friendGroupService) {
		this.mongo = mongo;
		this.feedService = feedService;
		this.friendGroupService = friendGroupService;
	}

	/** The search index, and search words for events saved before search existed. */
	@EventListener(ApplicationReadyEvent.class)
	public void prepareIndex() {
		try {
			mongo.indexOps(EventDoc.class)
					.ensureIndex(new Index().on("searchTerms", Sort.Direction.ASC).named("searchTerms"));
			Query missing = Query.query(Criteria.where("searchTerms").exists(false)).limit(500);
			int count = 0;
			List<EventDoc> batch;
			while (!(batch = mongo.find(missing, EventDoc.class)).isEmpty()) {
				for (EventDoc event : batch) {
					mongo.updateFirst(Query.query(Criteria.where("_id").is(event.getId())),
							Update.update("searchTerms", SearchText.terms(event)), EventDoc.class);
				}
				count += batch.size();
			}
			if (count > 0) {
				LOG.info("Added search words to {} existing events", count);
			}
		} catch (RuntimeException e) {
			LOG.warn("Could not prepare the event search index", e);
		}
	}

	public FeedPage search(String viewerEmail, String query, int page, int size) {
		return search(viewerEmail, query, page, size, Instant.now());
	}

	FeedPage search(String viewerEmail, String query, int page, int size, Instant now) {
		int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
		int pageIndex = Math.max(0, page);
		List<String> words = SearchText.queryTokens(StringUtils.left(query, MAX_QUERY_LENGTH), MAX_QUERY_WORDS);
		if (words.isEmpty()) {
			return new FeedPage(Collections.emptyList(), pageIndex, false).withTotal(0);
		}

		FeedService.Viewer viewer = feedService.viewer(viewerEmail);
		// Friends who keep the events they go to to themselves: their name must not find those events
		feedService.rememberGoingVisibility(viewer, viewer.friendsById.values());
		Names names = new Names(words, viewer, viewer.id == null ? Collections.emptyList()
				: friendGroupService.groupsOf(viewerEmail));
		List<EventDoc> candidates = mongo.find(candidateQuery(words, viewer, names, now), EventDoc.class);
		List<Hit> hits = rank(candidates, words, viewer, names, now);

		long from = (long) pageIndex * pageSize;
		if (from >= hits.size()) {
			return new FeedPage(Collections.emptyList(), pageIndex, false).withTotal(hits.size());
		}
		int to = (int) Math.min(hits.size(), from + pageSize);
		Map<String, User> hosts = new HashMap<>();
		List<FeedEvent> cards = hits.subList((int) from, to).stream().map(hit -> {
			FeedEvent card = feedService.toFeedEvent(hit.event, viewer, hosts, Collections.emptyMap());
			card.setMatchedBy(hit.matchedBy);
			return card;
		}).collect(Collectors.toList());
		return new FeedPage(cards, pageIndex, to < hits.size()).withTotal(hits.size());
	}

	/**
	 * The searcher's friends and groups whose names match a typed word ("anna" finds friend Anna Schmidt, "climb"
	 * the group "Climbing crew"). Only friends and the searcher's own groups, so nobody else's plans are revealed.
	 */
	static final class Names {
		/** typed word -> ids of friends with a name starting with it */
		final Map<String, Set<String>> friends = new HashMap<>();
		/** typed word -> ids of the searcher's groups with a name starting with it */
		final Map<String, Set<String>> groups = new HashMap<>();
		/** friend email -> id, to recognise friends hosting (events store the creator's email) */
		final Map<String, String> friendIdByEmail = new HashMap<>();
		/** ids of friends who keep the events they go to to themselves (going visibility only_me) */
		final Set<String> goingHidden = new HashSet<>();

		Names(List<String> words, FeedService.Viewer viewer, List<FriendGroup> myGroups) {
			viewer.friendsById.forEach((id, friend) -> {
				if (viewer.hidesGoing.getOrDefault(friend.getId(), false)) {
					goingHidden.add(id);
				}
				if (friend.getEmail() != null) {
					friendIdByEmail.put(friend.getEmail(), id);
				}
				List<String> nameWords = SearchText.tokens(Stream.of(friend.getFirstName(), friend.getLastName(),
						friend.getUsername()).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ")));
				words.stream().filter(word -> startsWithWord(nameWords, word))
						.forEach(word -> friends.computeIfAbsent(word, w -> new HashSet<>()).add(id));
			});
			for (FriendGroup group : myGroups) {
				List<String> nameWords = SearchText.tokens(group.getName());
				words.stream().filter(word -> startsWithWord(nameWords, word))
						.forEach(word -> groups.computeIfAbsent(word, w -> new HashSet<>())
								.add(String.valueOf(group.getId())));
			}
		}

		Set<String> allFriendIds() {
			return friends.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		}

		Set<String> allGroupIds() {
			return groups.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		}

		private static boolean startsWithWord(List<String> nameWords, String word) {
			String stem = SearchText.stem(word);
			return nameWords.stream().anyMatch(n -> n.startsWith(stem));
		}
	}

	private static Query candidateQuery(List<String> words, FeedService.Viewer viewer, Names names, Instant now) {
		Criteria visible = viewer.id == null ? Criteria.where("isPublic").is(true)
				: new Criteria().orOperator(Criteria.where("isPublic").is(true),
						Criteria.where("creatorId").is(viewer.email),
						Criteria.where("participantIds").is(viewer.id),
						Criteria.where("attendeeIds").is(viewer.id),
						Criteria.where("maybeIds").is(viewer.id));

		List<Criteria> matches = new ArrayList<>();
		// Words are letters and digits only (SearchText), so they need no escaping. The stem is a prefix of the
		// word, so "^stem" also finds the word itself; an anchored prefix uses the index.
		for (String word : words) {
			matches.add(Criteria.where("searchTerms").regex("^" + SearchText.stem(word)));
		}
		Set<String> friendIds = names.allFriendIds();
		if (!friendIds.isEmpty()) {
			List<String> emails = names.friendIdByEmail.entrySet().stream()
					.filter(e -> friendIds.contains(e.getValue())).map(Map.Entry::getKey)
					.collect(Collectors.toList());
			matches.add(Criteria.where("creatorId").in(emails));
			// Events a friend goes to, unless they keep that to themselves (hosting is always public)
			Set<String> goingVisible = new HashSet<>(friendIds);
			goingVisible.removeAll(names.goingHidden);
			if (!goingVisible.isEmpty()) {
				matches.add(Criteria.where("attendeeIds").in(goingVisible));
			}
			for (String id : goingVisible) {
				matches.add(Criteria.where("responses." + id).is("accepted"));
			}
		}
		Set<String> groupIds = names.allGroupIds();
		if (!groupIds.isEmpty()) {
			matches.add(Criteria.where("groupIds").in(groupIds));
		}

		return new Query(new Criteria().andOperator(Criteria.where("endTime").gt(now), Criteria.where("cancelled").ne(true), visible,
				new Criteria().orOperator(matches.toArray(new Criteria[0]))))
				.with(Sort.by("startTime").ascending())
				.limit(MAX_CANDIDATES);
	}

	/** One ranked result. */
	static final class Hit {
		final EventDoc event;
		final double score;
		final List<String> matchedBy;

		Hit(EventDoc event, double score, List<String> matchedBy) {
			this.event = event;
			this.score = score;
			this.matchedBy = matchedBy;
		}
	}

	/** An event's text, split into words once. */
	private static final class EventWords {
		final Map<String, List<String>> fields = new LinkedHashMap<>();
		final Map<String, Double> weights = new HashMap<>();

		EventWords(EventDoc event) {
			add("title", TITLE, event.getTitle());
			add("tags", TAGS, String.join(" ", SearchText.tagsOf(event)));
			add("location", PLACE, SearchText.placeOf(event));
			add("description", DESCRIPTION, event.getDescription());
		}

		private void add(String field, double weight, String text) {
			fields.put(field, SearchText.tokens(text));
			weights.put(field, weight);
		}
	}

	static List<Hit> rank(List<EventDoc> candidates, List<String> words, FeedService.Viewer viewer, Names names,
			Instant now) {
		int n = candidates.size();
		double[][] wordScores = new double[n][words.size()];
		List<Set<String>> reasons = new ArrayList<>();

		for (int i = 0; i < n; i++) {
			EventDoc event = candidates.get(i);
			EventWords text = new EventWords(event);
			Set<String> going = FeedService.goingIds(event);
			String friendHost = names.friendIdByEmail.get(event.getCreatorId());
			List<String> eventGroups = event.getGroupIds() == null ? Collections.emptyList() : event.getGroupIds();
			Set<String> why = new LinkedHashSet<>();

			for (int j = 0; j < words.size(); j++) {
				String word = words.get(j);
				Map<String, Double> found = new LinkedHashMap<>();
				text.fields.forEach((field, tokens) -> {
					double match = match(tokens, word);
					if (match > 0) {
						found.put(field, text.weights.get(field) * match);
					}
				});
				Set<String> friends = names.friends.getOrDefault(word, Collections.emptySet());
				if (friends.stream().anyMatch(id -> (going.contains(id) && !names.goingHidden.contains(id))
						|| id.equals(friendHost))) {
					found.put("friend", PERSON);
				}
				Set<String> groups = names.groups.getOrDefault(word, Collections.emptySet());
				if (eventGroups.stream().anyMatch(groups::contains)) {
					found.put("group", GROUP);
				}
				if (!found.isEmpty()) {
					double best = Collections.max(found.values());
					double sum = found.values().stream().mapToDouble(Double::doubleValue).sum();
					wordScores[i][j] = best + OTHER_FIELDS * (sum - best);
					why.addAll(found.keySet());
				}
			}
			reasons.add(why);
		}

		// Rare words count more: inverse document frequency over the matching events, as in BM25
		double[] idf = new double[words.size()];
		for (int j = 0; j < words.size(); j++) {
			int withWord = 0;
			for (int i = 0; i < n; i++) {
				if (wordScores[i][j] > 0) {
					withWord++;
				}
			}
			idf[j] = Math.log(1 + (n - withWord + 0.5) / (withWord + 0.5));
		}

		int[] matchedWords = new int[n];
		boolean anyMatchesAll = false;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < words.size(); j++) {
				if (wordScores[i][j] > 0) {
					matchedWords[i]++;
				}
			}
			anyMatchesAll |= matchedWords[i] == words.size();
		}

		String phrase = String.join(" ", words);
		List<Hit> hits = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (matchedWords[i] == 0 || (anyMatchesAll && matchedWords[i] < words.size())) {
				continue;
			}
			EventDoc event = candidates.get(i);
			double relevance = 0;
			for (int j = 0; j < words.size(); j++) {
				relevance += idf[j] * wordScores[i][j];
			}
			double coverage = (double) matchedWords[i] / words.size();
			relevance *= coverage * coverage;
			if (words.size() > 1 && String.join(" ", SearchText.tokens(event.getTitle())).contains(phrase)) {
				relevance += PHRASE_BONUS;
			}
			double score = relevance * (1 + personalBoost(event, viewer, names)) * soonness(event, now);
			hits.add(new Hit(event, score, new ArrayList<>(reasons.get(i))));
		}

		hits.sort(Comparator.comparingDouble((Hit h) -> -h.score)
				.thenComparing(h -> h.event.getStartTime(), Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(h -> h.event.getId(), Comparator.nullsLast(Comparator.naturalOrder())));
		return hits;
	}

	/**
	 * How well a typed word matches a field: 0 when it does not; otherwise WHOLE_WORD or WORD_START, scaled by how
	 * often it occurs with diminishing returns (once = 1, three times = about 1.6). "Whole word" compares stems, so
	 * "concert" matches "concerts" as a whole word.
	 */
	static double match(List<String> tokens, String word) {
		String stem = SearchText.stem(word);
		int whole = 0;
		int start = 0;
		for (String token : tokens) {
			if (token.equals(word) || SearchText.stem(token).equals(stem)) {
				whole++;
			} else if (token.startsWith(stem)) {
				start++;
			}
		}
		if (whole > 0) {
			return WHOLE_WORD * saturate(whole + start);
		}
		return start > 0 ? WORD_START * saturate(start) : 0;
	}

	private static double saturate(int count) {
		return count * (K1 + 1) / (count + K1);
	}

	/** Friends going, a friend hosting, your own event: up to about +1.05, multiplied into the score. */
	static double personalBoost(EventDoc event, FeedService.Viewer viewer, Names names) {
		if (viewer.id == null) {
			return 0;
		}
		Set<String> going = FeedService.goingIds(event);
		long friendsGoing = going.stream()
				.filter(id -> viewer.friendsById.containsKey(id) && !names.goingHidden.contains(id)).count();
		boolean friendHosting = names.friendIdByEmail.containsKey(event.getCreatorId());
		boolean mine = viewer.email.equals(event.getCreatorId()) || going.contains(viewer.id)
				|| (event.getParticipantIds() != null && event.getParticipantIds().contains(viewer.id));
		return 0.15 * Math.min(friendsGoing, 4) + (friendHosting ? 0.2 : 0) + (mine ? 0.25 : 0);
	}

	/** 1.3 for an event starting now, 1.15 a day away, about 1.04 a week away. */
	static double soonness(EventDoc event, Instant now) {
		if (event.getStartTime() == null) {
			return 1;
		}
		double daysAway = Math.max(0, Duration.between(now, event.getStartTime()).toMinutes() / 1440.0);
		return 1 + 0.3 / (1 + daysAway);
	}
}
