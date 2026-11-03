package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;

class AvailabilityServiceTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final LocalDate DAY = LocalDate.of(2026, 11, 10); // a Tuesday
	private static final Instant NOW = DAY.minusDays(1).atStartOfDay(BERLIN).toInstant();

	private EventMongoRepository repository;
	private AvailabilityService service;
	private User me;
	private User dave;
	private List<EventDoc> myEvents;
	private List<EventDoc> davesEvents;

	private static User user(long id, String email) {
		User u = new User();
		u.setId(id);
		u.setEmail(email);
		return u;
	}

	private static Instant at(int hour, int minute) {
		return ZonedDateTime.of(DAY.getYear(), DAY.getMonthValue(), DAY.getDayOfMonth(), hour, minute, 0, 0, BERLIN).toInstant();
	}

	private static EventDoc event(String creator, Instant start, Instant end) {
		EventDoc e = new EventDoc();
		e.setCreatorId(creator);
		e.setStartTime(start);
		e.setEndTime(end);
		return e;
	}

	@BeforeEach
	void setUp() {
		repository = mock(EventMongoRepository.class);
		service = new AvailabilityService(repository);
		me = user(1, "me@example.com");
		dave = user(2, "dave@example.com");
		myEvents = new ArrayList<>();
		davesEvents = new ArrayList<>();
		when(repository.findCreatedOrParticipatingInRange(eq("me@example.com"), eq("1"), any(), any())).thenReturn(myEvents);
		when(repository.findCreatedOrParticipatingInRange(eq("dave@example.com"), eq("2"), any(), any())).thenReturn(davesEvents);
	}

	@SuppressWarnings("unchecked")
	private List<String> slotsOfTheDay(int minutes) {
		Map<String, Object> result = service.sharedFreeSlots(me, dave, DAY, 1, minutes, BERLIN, NOW);
		Map<String, Object> day = ((List<Map<String, Object>>) result.get("days")).get(0);
		return ((List<Map<String, Object>>) day.get("slots")).stream()
				.map(s -> s.get("part") + " " + ((Instant) s.get("start")).atZone(BERLIN).toLocalTime())
				.collect(Collectors.toList());
	}

	@Test
	void anEmptyDayOffersMorningAfternoonAndEvening() {
		assertEquals(Arrays.asList("Morning 09:00", "Afternoon 12:00", "Evening 17:00"), slotsOfTheDay(60));
	}

	@Test
	void bothCalendarsCount() {
		myEvents.add(event("me@example.com", at(9, 0), at(10, 30))); // my morning meeting
		davesEvents.add(event("dave@example.com", at(17, 0), at(19, 0))); // Dave's evening plans

		assertEquals(Arrays.asList("Morning 10:30", "Afternoon 12:00", "Evening 19:00"), slotsOfTheDay(60));
	}

	@Test
	void longEventsMustFitBeforeTenPm() {
		davesEvents.add(event("dave@example.com", at(9, 0), at(21, 0)));

		assertEquals(Collections.emptyList(), slotsOfTheDay(90)); // 21:00 + 90 min would end after 22:00
		assertEquals(Arrays.asList("Evening 21:00"), slotsOfTheDay(60));
	}

	@Test
	void declinedInvitationsDoNotMakeAnyoneBusy() {
		EventDoc declined = event("someone@example.com", at(9, 0), at(22, 0));
		declined.setParticipantIds(new ArrayList<>(Arrays.asList("2")));
		declined.getResponses().put("2", "declined");
		davesEvents.add(declined);

		assertEquals(3, slotsOfTheDay(60).size());
		assertTrue(service.isFree(dave, at(10, 0), at(11, 0)));
	}

	@Test
	void invitationsNotYetDeclinedAndJoinedEventsCountAsBusy() {
		EventDoc maybe = event("someone@example.com", at(10, 0), at(11, 0));
		maybe.setParticipantIds(new ArrayList<>(Arrays.asList("2")));
		maybe.getResponses().put("2", "maybe");
		EventDoc joined = event("someone@example.com", at(18, 0), at(19, 0));
		joined.setAttendeeIds(new ArrayList<>(Arrays.asList("2")));
		davesEvents.addAll(Arrays.asList(maybe, joined));

		assertFalse(service.isFree(dave, at(10, 30), at(11, 30)));
		assertFalse(service.isFree(dave, at(18, 30), at(19, 30)));
		assertTrue(service.isFree(dave, at(11, 0), at(12, 0)));
	}

	@Test
	void timesThatHavePassedAreNotSuggested() {
		Map<String, Object> result = service.sharedFreeSlots(me, dave, DAY, 1, 60, BERLIN, at(13, 10));
		assertTrue(result.toString().contains("Evening"));
		assertFalse(result.toString().contains("Morning"));
		@SuppressWarnings("unchecked")
		Map<String, Object> afternoon = ((List<Map<String, Object>>) ((List<Map<String, Object>>) result.get("days")).get(0).get("slots")).get(0);
		assertEquals(at(13, 30), afternoon.get("start"));
	}
}
