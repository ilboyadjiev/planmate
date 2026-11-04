package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.planmate.dto.FriendGroup;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.util.SearchText;

/** Ranking only; which events are fetched from Mongo is covered by EventSearchIntegrationTest. */
class EventSearchServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
	private static final String ME = "me@example.com"; // id 1
	private static final String ANNA = "anna@example.com"; // id 2, my friend

	private static final FeedService.Viewer GUEST = FeedService.Viewer.GUEST;
	private static final FeedService.Viewer VIEWER = viewer();

	private static FeedService.Viewer viewer() {
		User anna = new User();
		anna.setId(2L);
		anna.setEmail(ANNA);
		anna.setFirstName("Anna");
		anna.setLastName("Schmidt");
		anna.setUsername("annas");
		Map<String, User> friends = new HashMap<>();
		friends.put("2", anna);
		return new FeedService.Viewer(ME, "1", friends);
	}

	private static EventDoc event(String id, String title, String description) {
		EventDoc e = new EventDoc();
		e.setId(id);
		e.setTitle(title);
		e.setDescription(description);
		e.setCreatorId("someone@example.com");
		e.setIsPublic(true);
		e.setStartTime(NOW.plus(Duration.ofDays(3)));
		e.setEndTime(NOW.plus(Duration.ofDays(3)).plusSeconds(3600));
		return e;
	}

	private static List<String> ranked(String query, FeedService.Viewer viewer, List<FriendGroup> groups,
			EventDoc... events) {
		List<String> words = SearchText.queryTokens(query, EventSearchService.MAX_QUERY_WORDS);
		EventSearchService.Names names = new EventSearchService.Names(words, viewer, groups);
		return EventSearchService.rank(Arrays.asList(events), words, viewer, names, NOW).stream()
				.map(h -> h.event.getId()).collect(Collectors.toList());
	}

	private static List<String> ranked(String query, EventDoc... events) {
		return ranked(query, GUEST, Collections.emptyList(), events);
	}

	@Test
	void titleBeatsDescription() {
		EventDoc inDescription = event("description", "Evening out", "Some jazz later");
		EventDoc inTitle = event("title", "Jazz evening", "Drinks");
		assertEquals(Arrays.asList("title", "description"), ranked("jazz", inDescription, inTitle));
	}

	@Test
	void wholeWordBeatsTheStartOfALongerWord() {
		EventDoc start = event("start", "Concertina workshop", null);
		EventDoc whole = event("whole", "Concerts in the park", null);
		assertEquals(Arrays.asList("whole", "start"), ranked("concert", start, whole));
	}

	@Test
	void typingTheStartOfAWordFindsIt() {
		assertEquals(Collections.singletonList("e"), ranked("conc", event("e", "Concert", null)));
	}

	@Test
	void accentsAndCaseDoNotMatter() {
		EventDoc event = event("e", "Brunch", null);
		EventDoc.EventLocation location = new EventDoc.EventLocation();
		location.setCity("München");
		event.setLocation(location);
		assertEquals(Collections.singletonList("e"), ranked("MUNCHEN brunch", event));
	}

	@Test
	void eventsWithEveryWordComeFirstAndPartialMatchesAreDropped() {
		EventDoc both = event("both", "Jazz brunch", null);
		EventDoc one = event("one", "Jazz Jazz Jazz night", null);
		assertEquals(Collections.singletonList("both"), ranked("jazz brunch", one, both));
	}

	@Test
	void withoutAnyFullMatchTheBestPartialMatchesRemain() {
		EventDoc jazz = event("jazz", "Jazz night", null);
		EventDoc other = event("other", "Pub quiz", null);
		assertEquals(Collections.singletonList("jazz"), ranked("jazz salsa", jazz, other));
	}

	@Test
	void rareWordsCountMoreThanCommonOnes() {
		// "night" is everywhere, "salsa" only in one title; the salsa event should win over a night-heavy one
		EventDoc salsa = event("salsa", "Salsa", "night");
		EventDoc nights = event("nights", "Night night", "salsa");
		EventDoc a = event("a", "Night run", null);
		EventDoc b = event("b", "Night market", null);
		List<String> result = ranked("salsa night", salsa, nights, a, b);
		assertEquals("salsa", result.get(0));
	}

	@Test
	void wordsInARowInTheTitleGetABonus() {
		EventDoc apart = event("apart", "Night of jazz and food", null);
		EventDoc together = event("together", "Jazz night", null);
		assertEquals(Arrays.asList("together", "apart"), ranked("jazz night", apart, together));
	}

	@Test
	void aFriendsNameFindsTheEventsTheyGoTo() {
		EventDoc annaGoing = event("anna", "Climbing", null);
		annaGoing.setAttendeeIds(new ArrayList<>(Collections.singletonList("2")));
		EventDoc other = event("other", "Bouldering", null);
		List<String> words = Collections.singletonList("anna");
		EventSearchService.Names names = new EventSearchService.Names(words, VIEWER, Collections.emptyList());
		List<EventSearchService.Hit> hits = EventSearchService.rank(Arrays.asList(annaGoing, other), words, VIEWER,
				names, NOW);
		assertEquals(1, hits.size());
		assertEquals("anna", hits.get(0).event.getId());
		assertEquals(Collections.singletonList("friend"), hits.get(0).matchedBy);
	}

	@Test
	void aFriendsNameIsNotMatchedForGuests() {
		EventDoc annaGoing = event("anna", "Climbing", null);
		annaGoing.setAttendeeIds(new ArrayList<>(Collections.singletonList("2")));
		assertTrue(ranked("anna", annaGoing).isEmpty());
	}

	@Test
	void myGroupsNameFindsItsEvents() {
		FriendGroup group = new FriendGroup();
		group.setId(7L);
		group.setName("Climbing crew");
		EventDoc groupEvent = event("group", "Saturday session", null);
		groupEvent.setGroupIds(new ArrayList<>(Collections.singletonList("7")));
		assertEquals(Collections.singletonList("group"),
				ranked("crew", VIEWER, Collections.singletonList(group), groupEvent, event("x", "Other", null)));
	}

	@Test
	void friendsGoingReorderEquallyRelevantEvents() {
		EventDoc strangers = event("strangers", "Jazz night", null);
		EventDoc friends = event("friends", "Jazz night", null);
		friends.setAttendeeIds(new ArrayList<>(Collections.singletonList("2")));
		assertEquals(Arrays.asList("friends", "strangers"),
				ranked("jazz", VIEWER, Collections.emptyList(), strangers, friends));
	}

	@Test
	void friendsGoingDoNotLiftAMuchWeakerMatch() {
		EventDoc strongMatch = event("strong", "Jazz", null);
		EventDoc weakMatch = event("weak", "Board games", "maybe some jazzy music");
		weakMatch.setAttendeeIds(new ArrayList<>(Collections.singletonList("2")));
		weakMatch.setCreatorId(ANNA);
		assertEquals(Arrays.asList("strong", "weak"),
				ranked("jazz", VIEWER, Collections.emptyList(), weakMatch, strongMatch));
	}

	@Test
	void matchReasonsNameTheFields() {
		EventDoc event = event("e", "Jazz", "jazz all night");
		event.setTags(Arrays.asList("#jazz"));
		List<String> words = Collections.singletonList("jazz");
		EventSearchService.Names names = new EventSearchService.Names(words, GUEST, Collections.emptyList());
		assertEquals(Arrays.asList("title", "tags", "description"),
				EventSearchService.rank(Collections.singletonList(event), words, GUEST, names, NOW).get(0).matchedBy);
	}
}
