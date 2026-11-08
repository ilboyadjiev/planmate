package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;

class FeedServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
	private static final String ALICE = "alice@example.com"; // id 1, viewer
	private static final String BOB = "bob@example.com"; // id 2, Alice's friend
	private static final String CAROL = "carol@example.com"; // id 3, stranger

	private EventMongoRepository repository;
	private UserService users;
	private FriendGroupService groups;
	private FeedService feed;

	private static User account(long id, String email, String first, String last, String username) {
		User u = new User();
		u.setId(id);
		u.setEmail(email);
		u.setFirstName(first);
		u.setLastName(last);
		u.setUsername(username);
		return u;
	}

	private static EventDoc event(String id, String creator, Duration startsIn, String... attendeeIds) {
		EventDoc e = new EventDoc();
		e.setId(id);
		e.setTitle(id);
		e.setCreatorId(creator);
		e.setIsPublic(true);
		e.setStartTime(NOW.plus(startsIn));
		e.setEndTime(NOW.plus(startsIn).plusSeconds(3600));
		e.setAttendeeIds(new ArrayList<>(Arrays.asList(attendeeIds)));
		return e;
	}

	private void candidates(EventDoc... events) {
		when(repository.findUpcomingPublic(eq(NOW), any())).thenReturn(Arrays.asList(events));
	}

	private static List<String> ids(FeedService.FeedPage page) {
		return ids(page.getEvents());
	}

	private static List<String> ids(List<FeedEvent> cards) {
		return cards.stream().map(FeedEvent::getId).collect(Collectors.toList());
	}

	@BeforeEach
	void setUp() {
		repository = mock(EventMongoRepository.class);
		users = mock(UserService.class);
		FriendshipService friendships = mock(FriendshipService.class);
		groups = mock(FriendGroupService.class);
		feed = new FeedService(repository, users, friendships, groups);

		User alice = account(1, ALICE, "Alice", "Meier", "alice");
		User bob = account(2, BOB, "Bob", null, "bob");
		when(users.getUserByEmail(ALICE)).thenReturn(alice);
		when(users.getUserByEmail(BOB)).thenReturn(bob);
		when(users.getUserByEmail(CAROL)).thenReturn(account(3, CAROL, null, null, "carol"));

		Friendship aliceBob = new Friendship();
		aliceBob.setUserA(alice);
		aliceBob.setUserB(bob);
		aliceBob.setStatus("accepted");
		when(friendships.getFriendshipList(ALICE)).thenReturn(Collections.singletonList(aliceBob));
	}

	@Test
	void friendsGoingOutrankASoonerOrBiggerEvent() {
		candidates(
				event("tonight-crowd", CAROL, Duration.ofHours(6), "10", "11", "12", "13", "14", "15", "16", "17"),
				event("next-week-with-bob", CAROL, Duration.ofDays(6), "2"),
				event("friend-hosts", BOB, Duration.ofDays(3)));

		assertEquals(Arrays.asList("next-week-with-bob", "friend-hosts", "tonight-crowd"),
				ids(feed.getFeed(ALICE, null, 0, 10, NOW)));
	}

	@Test
	void aDateWindowKeepsOnlyEventsInRangeOnTopOfTheOtherFilters() {
		candidates(event("in-one-day", BOB, Duration.ofDays(1)), event("in-five-days", CAROL, Duration.ofDays(5)),
				event("in-ten-days", BOB, Duration.ofDays(10)));
		Instant from = NOW.plus(Duration.ofDays(4));
		Instant to = NOW.plus(Duration.ofDays(6));

		assertEquals(Arrays.asList("in-five-days"), ids(feed.getFeed(ALICE, null, 0, 10, NOW, from, to)));
		// An open-ended "to" keeps everything from the start day on (order is up to ranking)
		assertEquals(new java.util.HashSet<>(Arrays.asList("in-five-days", "in-ten-days")),
				new java.util.HashSet<>(ids(feed.getFeed(ALICE, null, 0, 10, NOW, from, null))));
		// No window is the same feed as before
		assertEquals(3, feed.getFeed(ALICE, null, 0, 10, NOW, null, null).getEvents().size());
	}

	@Test
	void theWindowKeepsMultiDayEventsThatOverlapIt() {
		EventDoc exhibition = event("exhibition", BOB, Duration.ofDays(1));
		exhibition.setEndTime(NOW.plus(Duration.ofDays(10))); // runs day 1 to day 10

		// A window in the middle of the run (days 4-6) still keeps it
		assertEquals(true, FeedService.matchesWindow(exhibition, NOW.plus(Duration.ofDays(4)), NOW.plus(Duration.ofDays(6))));
		// A window entirely after it does not
		assertEquals(false, FeedService.matchesWindow(exhibition, NOW.plus(Duration.ofDays(11)), NOW.plus(Duration.ofDays(12))));
		// A window entirely before it does not
		assertEquals(false, FeedService.matchesWindow(exhibition, NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))));
	}

	@Test
	void anAllDayEventEndingAtTheWindowStartDoesNotLeakIn() {
		Instant from = NOW.plus(Duration.ofDays(2));
		Instant to = from.plus(Duration.ofDays(1));
		EventDoc allDayDayBefore = event("all-day", BOB, Duration.ofDays(1));
		allDayDayBefore.setStartTime(from.minus(Duration.ofDays(1))); // midnight the day before
		allDayDayBefore.setEndTime(from);                             // to midnight = the window start
		assertEquals(false, FeedService.matchesWindow(allDayDayBefore, from, to));

		// A single-moment event (no end) exactly at the window start is kept
		EventDoc atStart = event("at-start", BOB, Duration.ofDays(2));
		atStart.setStartTime(from);
		atStart.setEndTime(null);
		assertEquals(true, FeedService.matchesWindow(atStart, from, to));
	}

	@Test
	void guestsGetSoonAndPopularFirst() {
		candidates(event("later", BOB, Duration.ofDays(5), "2"), event("tomorrow", CAROL, Duration.ofDays(1)));

		assertEquals(Arrays.asList("tomorrow", "later"), ids(feed.getFeed(null, null, 0, 10, NOW)));
	}

	@Test
	void cardNamesTheHostAndOnlyFriendsNeverEmails() {
		candidates(event("party", BOB, Duration.ofDays(1), "2", "3", "1"));

		FeedEvent card = feed.getFeed(ALICE, null, 0, 10, NOW).getEvents().get(0);

		assertEquals("Bob", card.getHost().getName());
		assertEquals("bob", card.getHost().getUsername());
		assertTrue(card.getHost().isFriend());
		assertEquals(3, card.getAttendeeCount());
		assertEquals(1, card.getFriendsGoing().size()); // Bob; Carol is only part of the count
		assertEquals("Bob", card.getFriendsGoing().get(0).getFirstName());
		assertTrue(card.isGoing());
		assertFalse(card.isHosting());
	}

	@Test
	void cardsLinkToTheEventsSourcePage() {
		EventDoc picked = event("tm-1", CAROL, Duration.ofDays(1));
		picked.setSourceUrl("https://tickets.example/1");
		candidates(picked, event("own", BOB, Duration.ofDays(2)));

		java.util.Map<String, FeedEvent> cards = new java.util.HashMap<>();
		feed.getFeed(ALICE, null, 0, 10, NOW).getEvents().forEach(c -> cards.put(c.getId(), c));

		assertEquals("https://tickets.example/1", cards.get("tm-1").getSourceUrl());
		assertNull(cards.get("own").getSourceUrl());
	}

	@Test
	void ownEventsAreMarkedAsHosting() {
		candidates(event("mine", ALICE, Duration.ofDays(1)));

		FeedEvent card = feed.getFeed(ALICE, null, 0, 10, NOW).getEvents().get(0);

		assertTrue(card.isHosting());
		assertTrue(card.getHost().isYou());
		assertEquals("Alice Meier", card.getHost().getName());
	}

	@Test
	void hostFallsBackToUsernameAndGuestsStayAnonymous() {
		candidates(event("by-carol", CAROL, Duration.ofDays(1)), event("by-guest", "guest_abcdefghij0123", Duration.ofDays(2)));

		List<FeedEvent> cards = feed.getFeed(null, null, 0, 10, NOW).getEvents();

		assertEquals("carol", cards.get(0).getHost().getName());
		assertEquals("Guest", cards.get(1).getHost().getName());
		assertTrue(cards.get(1).getFriendsGoing().isEmpty());
	}

	@Test
	void pagesThroughTheRankedListAndCapsThePageSize() {
		candidates(event("a", CAROL, Duration.ofDays(1)), event("b", CAROL, Duration.ofDays(2)),
				event("c", CAROL, Duration.ofDays(3)));

		FeedService.FeedPage first = feed.getFeed(null, null, 0, 2, NOW);
		FeedService.FeedPage second = feed.getFeed(null, null, 1, 2, NOW);
		FeedService.FeedPage beyond = feed.getFeed(null, null, 99, 2, NOW);

		assertEquals(Arrays.asList("a", "b"), ids(first));
		assertTrue(first.isHasMore());
		assertEquals(Arrays.asList("c"), ids(second));
		assertFalse(second.isHasMore());
		assertTrue(beyond.getEvents().isEmpty());
	}

	@Test
	void pageSizeIsCapped() {
		EventDoc[] many = new EventDoc[FeedService.MAX_PAGE_SIZE + 10];
		for (int i = 0; i < many.length; i++) {
			many[i] = event("e" + i, CAROL, Duration.ofHours(i + 1));
		}
		candidates(many);

		assertEquals(FeedService.MAX_PAGE_SIZE, feed.getFeed(null, null, 0, 100000, NOW).getEvents().size());
	}

	@Test
	void tagsAreNormalisedBeforeQuerying() {
		when(repository.findUpcomingPublicWithTag(eq(NOW), eq("#munich"), any())).thenReturn(Collections.emptyList());

		feed.getFeed(null, "  Munich ", 0, 5, NOW);

		verify(repository).findUpcomingPublicWithTag(eq(NOW), eq("#munich"), any());
	}

	private static com.planmate.dto.FriendGroup boardGames(User... members) {
		com.planmate.dto.FriendGroup group = new com.planmate.dto.FriendGroup();
		group.setId(7L);
		group.setName("Board games");
		group.getMembers().addAll(Arrays.asList(members));
		return group;
	}

	private static EventDoc invite(String id, String creator, Duration startsIn, String... participantIds) {
		EventDoc e = event(id, creator, startsIn);
		e.setIsPublic(false);
		e.setParticipantIds(new ArrayList<>(Arrays.asList(participantIds)));
		e.setGroupIds(new ArrayList<>(Arrays.asList("7")));
		return e;
	}

	@Test
	void groupFeedShowsInvitesWithTheViewersAnswerAndNamesFellowMembers() {
		User carol = account(3, CAROL, null, null, "carol"); // not Alice's friend, but in her group
		when(groups.groupsOf(ALICE)).thenReturn(Collections.singletonList(boardGames(account(1, ALICE, "Alice", "Meier", "alice"), carol)));
		EventDoc dixit = invite("dixit", CAROL, Duration.ofDays(1), "1");
		dixit.getResponses().put("3", "accepted");
		EventDoc catan = invite("catan", CAROL, Duration.ofDays(2), "1");
		catan.getResponses().put("1", "maybe");
		when(repository.findUpcomingForGroupMember(eq("7"), eq(NOW), eq(ALICE), eq("1"), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(dixit, catan)));

		List<FeedEvent> cards = feed.getGroupFeed(ALICE, 7L, 0, 10, NOW).getEvents();

		assertTrue(cards.get(0).isInvited());
		assertEquals("pending", cards.get(0).getMyResponse());
		assertEquals(1, cards.get(0).getAttendeeCount()); // Carol accepted
		assertEquals("carol", cards.get(0).getFriendsGoing().get(0).getUsername()); // named: same group
		assertEquals("maybe", cards.get(1).getMyResponse());
		assertFalse(cards.get(1).isGoing());
		assertEquals(Boolean.FALSE, cards.get(0).getIsPublic()); // the card shows a lock
	}

	@Test
	void onlyMembersCanReadAGroupsFeed() {
		when(groups.groupsOf(ALICE)).thenReturn(Collections.emptyList());

		org.springframework.web.server.ResponseStatusException denied = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class, () -> feed.getGroupFeed(ALICE, 7L, 0, 10, NOW));
		assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, denied.getStatus());
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> feed.getGroupFeed(null, 7L, 0, 10, NOW));
	}

	@Test
	void acceptedInvitationsCountAsGoingInTheMainFeedToo() {
		EventDoc party = event("party", CAROL, Duration.ofDays(1), "2");
		party.setParticipantIds(new ArrayList<>(Arrays.asList("1")));
		party.getResponses().put("1", "accepted");
		candidates(party);

		FeedEvent card = feed.getFeed(ALICE, null, 0, 10, NOW).getEvents().get(0);

		assertEquals(2, card.getAttendeeCount()); // Bob joined, Alice accepted
		assertTrue(card.isGoing());
		assertTrue(card.isInvited());
		assertEquals("accepted", card.getMyResponse());
	}

	@Test
	void aroundMeAsksForACityWhenTheProfileHasNone() {
		FeedService.FeedPage page = feed.getNearbyFeed(ALICE, 0, 10, NOW);

		assertTrue(page.getEvents().isEmpty());
		assertEquals(null, page.getCity());
		verify(repository, org.mockito.Mockito.never()).findUpcomingPublicInCity(any(), any(), any(), any(), any());
	}

	@Test
	void aroundMeShowsRankedEventsForTheProfileCityUnderAnySpelling() {
		com.planmate.dto.Contact contact = new com.planmate.dto.Contact();
		contact.setCity("  München ");
		users.getUserByEmail(ALICE).setContactData(contact);
		when(repository.findUpcomingPublicInCity(eq(NOW), any(), any(), eq("#munich"), any()))
				.thenReturn(Arrays.asList(event("later", CAROL, Duration.ofDays(5)), event("tomorrow", CAROL, Duration.ofDays(1))));

		FeedService.FeedPage page = feed.getNearbyFeed(ALICE, 0, 10, NOW);

		assertEquals("München", page.getCity());
		assertEquals(Arrays.asList("tomorrow", "later"), ids(page));
	}

	@Test
	void forYouLeavesOutDiscoveredEventsFromOtherCities() {
		com.planmate.dto.Contact contact = new com.planmate.dto.Contact();
		contact.setCity("München");
		users.getUserByEmail(ALICE).setContactData(contact);
		EventDoc munich = discovered("munich", "#munich");
		EventDoc sofia = discovered("sofia", "#sofia");
		EventDoc release = discovered("release", null);
		EventDoc friendInSofia = event("friend-in-sofia", CAROL, Duration.ofDays(2));
		friendInSofia.setCityTag("#sofia");
		candidates(munich, sofia, release, friendInSofia);

		assertEquals(Arrays.asList("munich", "release", "friend-in-sofia"), ids(feed.getFeed(ALICE, null, 0, 10, NOW)));
		// The city's own chip still shows its events, and so does the feed for someone without a city
		when(repository.findUpcomingPublicWithTag(eq(NOW), eq("#sofia"), any())).thenReturn(Arrays.asList(sofia));
		assertEquals(Arrays.asList("sofia"), ids(feed.getFeed(ALICE, "#sofia", 0, 10, NOW)));
		users.getUserByEmail(ALICE).setContactData(null);
		assertEquals(4, feed.getFeed(ALICE, null, 0, 10, NOW).getEvents().size());
	}

	private static EventDoc discovered(String id, String cityTag) {
		EventDoc e = event(id, "events@planmate.org", Duration.ofDays(1));
		e.setDiscoverySource("tm");
		e.setCityTag(cityTag);
		return e;
	}

	@Test
	void cityTagsAreWrittenLikeEventTags() {
		assertEquals("#munich", FeedService.cityTag("Munich"));
		assertEquals("#newyork", FeedService.cityTag("New York"));
	}

	// --- Variety and related events ---

	private static final String PICKS = "events@planmate.org";

	private static EventDoc show(String id, String title, String venue, Duration startsIn, String... tags) {
		EventDoc e = event(id, PICKS, startsIn);
		e.setTitle(title);
		EventDoc.EventLocation where = new EventDoc.EventLocation();
		where.setName(venue);
		where.setCity("Munich");
		e.setLocation(where);
		List<String> all = new ArrayList<>(Arrays.asList(tags));
		all.add("#munich");
		e.setTags(all);
		e.setCityTag("#munich");
		return e;
	}

	@Test
	void aShowListedOncePerDateGetsOneCardThatCountsItsOtherDates() {
		candidates(
				show("hullis-1", "HULLIS - Comedy. Dinner. Show.", "Hullis Theater", Duration.ofDays(1), "#comedy"),
				show("hullis-2", "HULLIS - Comedy. Dinner. Show.", "Hullis Theater", Duration.ofDays(2), "#comedy"),
				show("hullis-3", "hullis - comedy.  dinner. show.", "Hullis Theater", Duration.ofDays(3), "#comedy"),
				show("hullis-elsewhere", "HULLIS - Comedy. Dinner. Show.", "Other Stage", Duration.ofDays(4), "#comedy"),
				show("jazz", "Jazz Night", "Unterfahrt", Duration.ofDays(2), "#concert"));

		List<FeedEvent> cards = feed.getFeed(ALICE, null, 0, 10, NOW).getEvents();

		assertEquals(Arrays.asList("hullis-1", "jazz", "hullis-elsewhere"), ids(cards));
		assertEquals(Integer.valueOf(2), cards.get(0).getMoreDates());
		assertNull(cards.get(1).getMoreDates());
	}

	@Test
	void manyEventsOfOneTopicAreMixedWithOthers() {
		candidates(
				show("concert-1", "Band A", "Hall", Duration.ofHours(24), "#concert"),
				show("concert-2", "Band B", "Hall", Duration.ofHours(26), "#concert"),
				show("concert-3", "Band C", "Hall", Duration.ofHours(28), "#concert"),
				show("concert-4", "Band D", "Hall", Duration.ofHours(30), "#concert"),
				show("comedy", "Stand-up", "Club", Duration.ofHours(72), "#comedy"));

		assertEquals(Arrays.asList("concert-1", "comedy", "concert-2", "concert-3", "concert-4"),
				ids(feed.getFeed(null, null, 0, 10, NOW)));
	}

	@Test
	void relatedListsTheShowsOtherDatesAndSimilarEventsNearby() {
		EventDoc opened = show("hullis-2", "HULLIS - Comedy. Dinner. Show.", "Hullis Theater", Duration.ofDays(2), "#comedy", "#food");
		EventDoc first = show("hullis-1", "HULLIS - Comedy. Dinner. Show.", "Hullis Theater", Duration.ofDays(1), "#comedy", "#food");
		EventDoc third = show("hullis-3", "HULLIS - Comedy. Dinner. Show.", "Hullis Theater", Duration.ofDays(3), "#comedy", "#food");
		EventDoc otherVenue = show("hullis-x", "HULLIS - Comedy. Dinner. Show.", "Other Stage", Duration.ofDays(5), "#comedy");
		when(repository.findUpcomingPublicByCreatorAndTitle(eq(PICKS), eq(opened.getTitle()), eq(NOW), any()))
				.thenReturn(Arrays.asList(first, opened, third, otherVenue));

		EventDoc standUp = show("standup", "Stand-up Night", "Club", Duration.ofDays(4), "#comedy", "#food");
		EventDoc dinner = show("dinner", "Supper Club", "Kitchen", Duration.ofDays(1), "#food");
		EventDoc rock = show("rock", "Rock Night", "Hall", Duration.ofDays(1), "#concert");
		EventDoc berlin = show("berlin-comedy", "Comedy Berlin", "Bar", Duration.ofDays(1), "#comedy");
		berlin.setCityTag("#berlin");
		candidates(opened, first, third, otherVenue, standUp, dinner, rock, berlin);

		FeedService.RelatedEvents related = feed.related(opened, ALICE, NOW);

		assertEquals(Arrays.asList("hullis-1", "hullis-3"), ids(related.getMoreDates()));
		// Two shared topics beat one, then sooner first; the show's own dates, other topics and other cities are left out
		assertEquals(Arrays.asList("standup", "dinner", "hullis-x"), ids(related.getSimilar()));
	}

	@Test
	void privateEventsListNoOtherDates() {
		EventDoc secret = show("secret", "Team dinner", "Kitchen", Duration.ofDays(1), "#food");
		secret.setIsPublic(false);
		candidates();

		FeedService.RelatedEvents related = feed.related(secret, ALICE, NOW);

		assertTrue(related.getMoreDates().isEmpty());
		assertTrue(related.getSimilar().isEmpty());
	}
}
