package com.planmate.controller2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.service.CityTagService;
import com.planmate.service.EventMongoService;
import com.planmate.service.FeedService;
import com.planmate.service.FriendGroupService;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;

class EventMongoControllerTest {

	private static final String ALICE = "alice@example.com"; // user id 1
	private static final String BOB = "bob@example.com"; // user id 2, accepted friend of Alice
	private static final String CAROL = "carol@example.com"; // user id 3, not a friend of Alice
	private static final String GUEST = "guest_abcdefghij0123456789";
	private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");

	private EventMongoService service;
	private FriendGroupService groupService;
	private EventMongoController controller;

	private static Principal user(String email) {
		return () -> email;
	}

	private static User account(long id, String email) {
		User u = new User();
		u.setId(id);
		u.setEmail(email);
		u.setFirstName(email.substring(0, 1).toUpperCase() + email.substring(1, email.indexOf('@')));
		u.setUsername(email.substring(0, email.indexOf('@')));
		return u;
	}

	private static EventDoc event(String id, String creatorId, boolean isPublic, String... participantIds) {
		EventDoc e = new EventDoc();
		e.setId(id);
		e.setCreatorId(creatorId);
		e.setIsPublic(isPublic);
		e.setParticipantIds(new java.util.ArrayList<>(Arrays.asList(participantIds)));
		return e;
	}

	private static HttpStatus statusOf(Runnable call) {
		return assertThrows(ResponseStatusException.class, call::run).getStatus();
	}

	@BeforeEach
	void setUp() {
		service = mock(EventMongoService.class);
		UserService userService = mock(UserService.class);
		FriendshipService friendshipService = mock(FriendshipService.class);
		groupService = mock(FriendGroupService.class);
		FeedService feedService = new FeedService(mock(EventMongoRepository.class), userService, friendshipService, groupService);
		controller = new EventMongoController(service, userService, friendshipService, feedService, groupService,
				new CityTagService(), new com.planmate.service.CalendarExportService(null, "https://www.planmate.org"));

		User alice = account(1, ALICE);
		User bob = account(2, BOB);
		when(userService.getUserByEmail(ALICE)).thenReturn(alice);
		when(userService.getUserByEmail(BOB)).thenReturn(bob);
		when(userService.getUserByEmail(CAROL)).thenReturn(account(3, CAROL));
		when(userService.getUserById(1L)).thenReturn(alice);
		when(userService.getUserById(2L)).thenReturn(bob);
		when(userService.getUserById(3L)).thenReturn(account(3, CAROL));

		Friendship aliceBob = new Friendship();
		aliceBob.setUserA(alice);
		aliceBob.setUserB(bob);
		aliceBob.setStatus("accepted");
		when(friendshipService.getFriendshipList(ALICE)).thenReturn(Collections.singletonList(aliceBob));

		when(service.createEvent(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void createAsUserIgnoresClientSuppliedOwnerAndId() {
		EventDoc forged = event("someone-elses-event-id", BOB, false);

		controller.createEvent(forged, user(ALICE));

		ArgumentCaptor<EventDoc> saved = ArgumentCaptor.forClass(EventDoc.class);
		verify(service).createEvent(saved.capture());
		assertEquals(ALICE, saved.getValue().getCreatorId());
		assertNull(saved.getValue().getId());
	}

	@Test
	void createAsGuestRequiresValidGuestId() {
		controller.createEvent(event(null, GUEST, false), null);
		verify(service).createEvent(any());

		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.createEvent(event(null, BOB, false), null)));
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.createEvent(event(null, null, false), null)));
	}

	@Test
	void participantsMustBeAcceptedFriends() {
		controller.createEvent(event(null, null, false, "2", "2"), user(ALICE));
		ArgumentCaptor<EventDoc> saved = ArgumentCaptor.forClass(EventDoc.class);
		verify(service).createEvent(saved.capture());
		assertEquals(Collections.singletonList("2"), saved.getValue().getParticipantIds()); // de-duplicated

		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.createEvent(event(null, null, false, "3"), user(ALICE))));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.createEvent(event(null, GUEST, false, "2"), null)));
	}

	@Test
	void rangeForLoggedInUserIncludesInvitationsAndIgnoresCreatorIdParam() {
		controller.getEventsByRange(BOB, START, END, user(ALICE));

		verify(service).getVisibleEventsInRange(ALICE, "1", START, END);
	}

	@Test
	void rangeForGuestOnlyCoversOwnEvents() {
		controller.getEventsByRange(GUEST, START, END, null);

		verify(service).getVisibleEventsInRange(GUEST, null, START, END);
	}

	@Test
	void rangeAnonymousCannotReadAUsersCalendar() {
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.getEventsByRange(ALICE, START, END, null)));
		verify(service, never()).getVisibleEventsInRange(anyString(), any(), any(), any());
	}

	@Test
	void cannotUpdateOrDeleteSomeoneElsesEvent() {
		when(service.getEventById("e1")).thenReturn(event("e1", BOB, false));

		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.updateEvent("e1", event(null, BOB, false), user(ALICE))));
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.deleteEvent("e1", null, user(ALICE))));
		// A guest cannot claim a user's event by sending that user's email as guest id
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.updateEvent("e1", event(null, BOB, false), null)));

		verify(service, never()).updateEvent(anyString(), any());
		verify(service, never()).deleteEvent(anyString());
	}

	@Test
	void participantCanViewButNotChangeTheEvent() {
		when(service.getEventById("e1")).thenReturn(event("e1", ALICE, false, "2"));

		assertEquals(HttpStatus.OK, controller.getEventById("e1", null, user(BOB)).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.updateEvent("e1", event(null, null, false, "2"), user(BOB))));
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.deleteEvent("e1", null, user(BOB))));
		// Someone who is not invited cannot view it
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.getEventById("e1", null, user(CAROL))));

		verify(service, never()).updateEvent(anyString(), any());
		verify(service, never()).deleteEvent(anyString());
	}

	@Test
	void ownerCanUpdateAndDelete() {
		when(service.getEventById("e1")).thenReturn(event("e1", ALICE, false));

		controller.updateEvent("e1", event(null, null, false, "2"), user(ALICE));
		controller.deleteEvent("e1", null, user(ALICE));

		ArgumentCaptor<EventDoc> updated = ArgumentCaptor.forClass(EventDoc.class);
		verify(service).updateEvent(eq("e1"), updated.capture());
		assertEquals(Collections.singletonList("2"), updated.getValue().getParticipantIds());
		verify(service).deleteEvent("e1");
	}

	@Test
	void ownerCannotInviteNonFriendsOnUpdate() {
		when(service.getEventById("e1")).thenReturn(event("e1", ALICE, false));

		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.updateEvent("e1", event(null, null, false, "3"), user(ALICE))));
		verify(service, never()).updateEvent(anyString(), any());
	}

	@Test
	void guestCanEditOwnEvent() {
		when(service.getEventById("g1")).thenReturn(event("g1", GUEST, false));

		controller.updateEvent("g1", event(null, GUEST, false), null);

		verify(service).updateEvent(eq("g1"), any());
	}

	@Test
	void privateEventsAreOnlyVisibleToOwnerAndParticipants() {
		when(service.getEventById("private")).thenReturn(event("private", BOB, false));
		when(service.getEventById("public")).thenReturn(event("public", BOB, true));

		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.getEventById("private", null, user(ALICE))));
		assertEquals(HttpStatus.OK, controller.getEventById("public", null, user(ALICE)).getStatusCode());
		assertEquals(HttpStatus.OK, controller.getEventById("private", null, user(BOB)).getStatusCode());
	}

	@Test
	void mergeAlwaysTargetsTheCaller() {
		controller.mergeGuestEvents(GUEST, user(ALICE));
		verify(service).mergeGuestEvents(GUEST, ALICE);

		// Cannot use merge to pull another user's events (non-guest ids are rejected)
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.mergeGuestEvents(BOB, user(ALICE))));
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.mergeGuestEvents(GUEST, null)));
	}

	@Test
	void principalWithoutNameIsRejected() {
		when(service.getEventById("orphan")).thenReturn(event("orphan", null, false));

		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.getEventsByRange(null, START, END, user(null))));
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.deleteEvent("orphan", null, user(null))));
		verify(service, never()).deleteEvent(anyString());
	}

	private static EventDoc attended(String id, String creatorId, String... attendeeIds) {
		EventDoc e = event(id, creatorId, true, "2");
		e.setAttendeeIds(new ArrayList<>(Arrays.asList(attendeeIds)));
		e.setEndTime(Instant.now().plusSeconds(3600));
		return e;
	}

	@Test
	void rangeTellsHostsInviteesAndAttendeesApartAndHidesStrangersData() {
		EventDoc own = attended("own", ALICE, "3");
		EventDoc invited = event("invited", BOB, false, "1");
		EventDoc going = attended("going", CAROL, "1", "3");
		when(service.getVisibleEventsInRange(ALICE, "1", START, END)).thenReturn(Arrays.asList(own, invited, going));

		controller.getEventsByRange(null, START, END, user(ALICE));

		assertEquals("host", own.getViewerRole());
		assertEquals(Arrays.asList("3"), own.getAttendeeIds()); // the host sees who is coming

		assertEquals("invited", invited.getViewerRole());
		assertEquals(BOB, invited.getCreatorId()); // a friend: the organizer is shown from the friends list
		assertEquals("Bob", invited.getHostName());

		assertEquals("going", going.getViewerRole());
		assertEquals("Carol", going.getHostName());
		assertNull(going.getCreatorId()); // a stranger's email never reaches attendees
		assertTrue(going.getParticipantIds().isEmpty());
		assertTrue(going.getAttendeeIds().isEmpty());
	}

	@Test
	void publicEventDetailsNeverExposeTheCreatorsEmail() {
		when(service.getEventById("public")).thenReturn(attended("public", BOB, "3"));

		EventDoc seen = controller.getEventById("public", null, null).getBody();

		assertNull(seen.getCreatorId());
		assertEquals("Bob", seen.getHostName());
		assertTrue(seen.getParticipantIds().isEmpty());
		assertTrue(seen.getAttendeeIds().isEmpty());
	}

	@Test
	void sharedLinksOpenPublicEventsForEveryoneAndPrivateOnesOnlyForThoseInvolved() {
		EventDoc party = event("party", BOB, true);
		party.setTitle("Party");
		when(service.getEventById("party")).thenReturn(party);
		FeedEvent forGuest = controller.getEventCard("party", null).getBody();
		assertEquals("Party", forGuest.getTitle());
		assertEquals(Boolean.TRUE, forGuest.getIsPublic());
		assertEquals("Bob", forGuest.getHost().getName());

		EventDoc dinner = event("dinner", BOB, false, "1");
		when(service.getEventById("dinner")).thenReturn(dinner);
		assertEquals(Boolean.FALSE, controller.getEventCard("dinner", user(BOB)).getBody().getIsPublic());
		assertTrue(controller.getEventCard("dinner", user(BOB)).getBody().isHosting());
		FeedEvent forInvitee = controller.getEventCard("dinner", user(ALICE)).getBody();
		assertTrue(forInvitee.isInvited());
		assertEquals("pending", forInvitee.getMyResponse());

		// Everyone else gets no details, only why: log in first, or ask the host for an invitation
		assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.getEventCard("dinner", user(CAROL))));
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.getEventCard("dinner", null)));
		when(service.getEventById("gone")).thenThrow(new RuntimeException("not found"));
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.getEventCard("gone", null)));
	}

	@Test
	void relatedEventsFollowTheEventPagesRules() {
		EventDoc dinner = event("dinner", BOB, false, "1");
		when(service.getEventById("dinner")).thenReturn(dinner);
		assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> controller.getRelatedEvents("dinner", user(CAROL))));
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.getRelatedEvents("dinner", null)));
		assertEquals(HttpStatus.OK, controller.getRelatedEvents("dinner", user(ALICE)).getStatusCode());
	}

	@Test
	void joiningAndLeavingAPublicEvent() {
		EventDoc concert = attended("concert", BOB);
		when(service.getEventById("concert")).thenReturn(concert);

		controller.attend("concert", user(CAROL));
		verify(service).addAttendee("concert", "3");

		controller.leave("concert", user(CAROL));
		verify(service).removeAttendee("concert", "3");
	}

	@Test
	void joiningIsOnlyForLoggedInUsersOnUpcomingPublicEventsOfOthers() {
		when(service.getEventById("public")).thenReturn(attended("public", BOB));
		when(service.getEventById("private")).thenReturn(event("private", BOB, false));
		EventDoc over = attended("over", BOB);
		over.setEndTime(Instant.now().minusSeconds(60));
		when(service.getEventById("over")).thenReturn(over);

		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.attend("public", null)));
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> controller.attend("private", user(CAROL))));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.attend("public", user(BOB))));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.attend("over", user(CAROL))));
		verify(service, never()).addAttendee(anyString(), anyString());
	}

	@Test
	void onlyInviteesCanAnswerAndOnlyWithKnownAnswers() {
		EventDoc dinner = event("dinner", ALICE, false, "2");
		when(service.getEventById("dinner")).thenReturn(dinner);

		controller.respond("dinner", java.util.Map.of("response", "maybe"), user(BOB));
		verify(service).setResponse("dinner", "2", "maybe");

		assertEquals(HttpStatus.NOT_FOUND,
				statusOf(() -> controller.respond("dinner", java.util.Map.of("response", "accepted"), user(CAROL))));
		assertEquals(HttpStatus.BAD_REQUEST,
				statusOf(() -> controller.respond("dinner", java.util.Map.of("response", "yes please"), user(BOB))));
		assertEquals(HttpStatus.UNAUTHORIZED,
				statusOf(() -> controller.respond("dinner", java.util.Map.of("response", "accepted"), null)));
		verify(service, never()).setResponse(eq("dinner"), eq("3"), anyString());
	}

	@Test
	void answersAreSharedWithTheInvitedButNotWithStrangers() {
		EventDoc invited = event("invited", BOB, false, "1", "3");
		invited.getResponses().put("3", "declined");
		EventDoc going = attended("going", CAROL, "1");
		going.getResponses().put("2", "accepted");
		when(service.getVisibleEventsInRange(ALICE, "1", START, END)).thenReturn(Arrays.asList(invited, going));

		controller.getEventsByRange(null, START, END, user(ALICE));

		assertEquals("declined", invited.getResponses().get("3"));
		assertTrue(going.getResponses().isEmpty());
	}

	@Test
	void clientCannotSeedAttendeesWhenCreating() {
		EventDoc forged = event(null, null, true);
		forged.setAttendeeIds(new ArrayList<>(Arrays.asList("2", "3")));
		forged.getResponses().put("2", "accepted");

		controller.createEvent(forged, user(ALICE));

		ArgumentCaptor<EventDoc> saved = ArgumentCaptor.forClass(EventDoc.class);
		verify(service).createEvent(saved.capture());
		assertTrue(saved.getValue().getAttendeeIds().isEmpty());
		assertTrue(saved.getValue().getResponses().isEmpty());
	}

	/** "Board games" (id 7): Alice, Bob and Carol. Carol is not Alice's friend, but they share the group. */
	private void aliceIsInBoardGames() {
		FriendGroup boardGames = new FriendGroup();
		boardGames.setId(7L);
		boardGames.setName("Board games");
		boardGames.getMembers().addAll(Arrays.asList(account(1, ALICE), account(2, BOB), account(3, CAROL)));
		when(groupService.groupsOf(ALICE)).thenReturn(Collections.singletonList(boardGames));
	}

	@Test
	void invitingAGroupInvitesAllItsMembersAndRemembersTheGroup() {
		aliceIsInBoardGames();
		EventDoc dixit = event(null, null, false);
		dixit.setGroupIds(new ArrayList<>(Arrays.asList("7")));

		controller.createEvent(dixit, user(ALICE));

		ArgumentCaptor<EventDoc> saved = ArgumentCaptor.forClass(EventDoc.class);
		verify(service).createEvent(saved.capture());
		assertEquals(Arrays.asList("2", "3"), saved.getValue().getParticipantIds()); // never Alice herself
		assertEquals(Arrays.asList("7"), saved.getValue().getGroupIds());
	}

	@Test
	void fellowGroupMembersCanOnlyBeInvitedTogetherWithTheGroup() {
		aliceIsInBoardGames();

		// Carol alone: not a friend
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.createEvent(event(null, null, false, "3"), user(ALICE))));

		EventDoc withGroup = event(null, null, false, "3");
		withGroup.setGroupIds(new ArrayList<>(Arrays.asList("7")));
		controller.createEvent(withGroup, user(ALICE));
		verify(service).createEvent(any());
	}

	@Test
	void onlyGroupsYouAreInCanBeInvited() {
		aliceIsInBoardGames();
		EventDoc foreign = event(null, null, false);
		foreign.setGroupIds(new ArrayList<>(Arrays.asList("8")));
		EventDoc guest = event(null, GUEST, false);
		guest.setGroupIds(new ArrayList<>(Arrays.asList("7")));

		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.createEvent(foreign, user(ALICE))));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> controller.createEvent(guest, null)));
		verify(service, never()).createEvent(any());
	}

	@Test
	void strangersNeverSeeWhichGroupsWereInvited() {
		EventDoc party = attended("party", BOB, "1");
		party.setGroupIds(new ArrayList<>(Arrays.asList("7")));
		when(service.getEventById("party")).thenReturn(party);

		assertTrue(controller.getEventById("party", null, user(CAROL)).getBody().getGroupIds().isEmpty());
	}

	@Test
	void eventsAreTaggedWithTheirCityAndTheTagFollowsTheLocation() {
		EventDoc dixit = event(null, null, false);
		dixit.setTitle("Dixit at Café Spielwiese");
		EventDoc.EventLocation munich = new EventDoc.EventLocation();
		munich.setName("Café Spielwiese");
		munich.setCity("München");
		dixit.setLocation(munich);
		dixit.setTags(new ArrayList<>(Arrays.asList("#boardgames")));

		controller.createEvent(dixit, user(ALICE));
		assertEquals(Arrays.asList("#boardgames", "#munich"), dixit.getTags());
		assertEquals("#munich", dixit.getCityTag());

		// Moved to Berlin: the old city tag goes, the new one comes
		EventDoc existing = event("e1", ALICE, false);
		existing.setCityTag("#munich");
		when(service.getEventById("e1")).thenReturn(existing);
		EventDoc moved = event(null, null, false);
		moved.setTitle("Dixit");
		moved.setTags(new ArrayList<>(Arrays.asList("#boardgames", "#munich")));
		moved.setLocation(new EventDoc.EventLocation());
		moved.getLocation().setName("Bar in Berlin Mitte");
		controller.updateEvent("e1", moved, user(ALICE));
		assertEquals(Arrays.asList("#boardgames", "#berlin"), moved.getTags());

		// The creator removed the city tag by hand: it is not added back
		existing.setCityTag("#berlin");
		EventDoc untagged = event(null, null, false);
		untagged.setTitle("Dixit in Berlin");
		untagged.setTags(new ArrayList<>(Arrays.asList("#boardgames")));
		controller.updateEvent("e1", untagged, user(ALICE));
		assertEquals(Arrays.asList("#boardgames"), untagged.getTags());
	}

	@Test
	void organizerAndInviteesGetEveryInviteesNameButNeverTheirEmail() {
		EventDoc dinner = event("dinner", ALICE, false, "2", "3"); // Carol (3) is not Alice's friend
		dinner.getResponses().put("3", "declined");
		when(service.getEventById("dinner")).thenReturn(dinner);

		EventDoc seen = controller.getEventById("dinner", null, user(ALICE)).getBody();

		assertEquals(Arrays.asList("bob", "carol"),
				seen.getParticipantProfiles().stream().map(p -> p.get("username")).collect(java.util.stream.Collectors.toList()));
		assertFalse(seen.getParticipantProfiles().toString().contains("@example.com"));
		assertEquals("declined", seen.getResponses().get("3"));
	}

	@Test
	void strangersGetNoInviteeNames() {
		EventDoc party = attended("party", BOB, "1");
		when(service.getEventById("party")).thenReturn(party);

		assertNull(controller.getEventById("party", null, user(CAROL)).getBody().getParticipantProfiles());
	}

	@Test
	void inviteesOnlyLearnAboutInvitedGroupsTheyAreIn() {
		aliceIsInBoardGames(); // group 7: Alice, Bob, Carol
		EventDoc birthday = event("birthday", CAROL, false, "1", "2");
		birthday.setGroupIds(new ArrayList<>(Arrays.asList("7", "8")));
		when(service.getEventById("birthday")).thenReturn(birthday);
		when(groupService.groupsOf(BOB)).thenReturn(Collections.emptyList());

		EventDoc asAlice = controller.getEventById("birthday", null, user(ALICE)).getBody();
		assertEquals(Arrays.asList("7"), asAlice.getGroupIds()); // not group 8, which Alice is not in
		assertEquals("carol", asAlice.getOrganizer().get("username"));
		assertFalse(asAlice.getOrganizer().containsKey("email"));

		birthday.setGroupIds(new ArrayList<>(Arrays.asList("7", "8")));
		EventDoc asBob = controller.getEventById("birthday", null, user(BOB)).getBody();
		assertTrue(asBob.getGroupIds().isEmpty()); // Bob is in neither group (in this test)
	}
}
