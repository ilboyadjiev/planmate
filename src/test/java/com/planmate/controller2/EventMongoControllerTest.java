package com.planmate.controller2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.service.EventMongoService;
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
	private EventMongoController controller;

	private static Principal user(String email) {
		return () -> email;
	}

	private static User account(long id, String email) {
		User u = new User();
		u.setId(id);
		u.setEmail(email);
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
		controller = new EventMongoController(service, userService, friendshipService);

		User alice = account(1, ALICE);
		User bob = account(2, BOB);
		when(userService.getUserByEmail(ALICE)).thenReturn(alice);
		when(userService.getUserByEmail(BOB)).thenReturn(bob);
		when(userService.getUserByEmail(CAROL)).thenReturn(account(3, CAROL));

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

	@Test
	void feedPageSizeIsCapped() {
		when(service.getEventsByTag(anyString(), any())).thenReturn(new PageImpl<>(Collections.<EventDoc>emptyList()));

		controller.getFeedEvents("#munich", 0, 100000);

		ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
		verify(service).getEventsByTag(eq("#munich"), paging.capture());
		assertEquals(50, paging.getValue().getPageSize());
	}
}
