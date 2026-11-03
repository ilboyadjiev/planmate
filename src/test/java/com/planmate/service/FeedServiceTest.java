package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		return page.getEvents().stream().map(FeedEvent::getId).collect(Collectors.toList());
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
	void cityTagsAreWrittenLikeEventTags() {
		assertEquals("#munich", FeedService.cityTag("Munich"));
		assertEquals("#newyork", FeedService.cityTag("New York"));
	}
}
