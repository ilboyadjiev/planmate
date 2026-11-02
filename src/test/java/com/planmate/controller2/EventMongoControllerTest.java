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
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dto2.EventDoc;
import com.planmate.service.EventMongoService;

class EventMongoControllerTest {

	private static final String ALICE = "alice@example.com";
	private static final String BOB = "bob@example.com";
	private static final String GUEST = "guest_abcdefghij0123456789";
	private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");

	private EventMongoService service;
	private EventMongoController controller;

	private static Principal user(String email) {
		return () -> email;
	}

	private static EventDoc event(String id, String creatorId, boolean isPublic) {
		EventDoc e = new EventDoc();
		e.setId(id);
		e.setCreatorId(creatorId);
		e.setIsPublic(isPublic);
		return e;
	}

	private static HttpStatus statusOf(Runnable call) {
		return assertThrows(ResponseStatusException.class, call::run).getStatus();
	}

	@BeforeEach
	void setUp() {
		service = mock(EventMongoService.class);
		controller = new EventMongoController(service);
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
	void rangeForLoggedInUserIgnoresCreatorIdParam() {
		controller.getEventsByRange(BOB, START, END, user(ALICE));

		verify(service).getEventsByCreatorAndRange(ALICE, START, END);
	}

	@Test
	void rangeAnonymousCannotReadAUsersCalendar() {
		assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> controller.getEventsByRange(ALICE, START, END, null)));
		verify(service, never()).getEventsByCreatorAndRange(anyString(), any(), any());
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
	void ownerCanUpdateAndDelete() {
		when(service.getEventById("e1")).thenReturn(event("e1", ALICE, false));

		controller.updateEvent("e1", event(null, null, false), user(ALICE));
		controller.deleteEvent("e1", null, user(ALICE));

		verify(service).updateEvent(eq("e1"), any());
		verify(service).deleteEvent("e1");
	}

	@Test
	void guestCanEditOwnEvent() {
		when(service.getEventById("g1")).thenReturn(event("g1", GUEST, false));

		controller.updateEvent("g1", event(null, GUEST, false), null);

		verify(service).updateEvent(eq("g1"), any());
	}

	@Test
	void privateEventsAreOnlyVisibleToTheirOwner() {
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
		when(service.getEventsByTag(anyString(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));

		controller.getFeedEvents("#munich", 0, 100000);

		ArgumentCaptor<org.springframework.data.domain.Pageable> paging = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
		verify(service).getEventsByTag(eq("#munich"), paging.capture());
		assertEquals(50, paging.getValue().getPageSize());
	}
}
