package com.planmate.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.planmate.PlanmateApplication;
import com.planmate.dto2.EventDoc;

/**
 * Runs the participant query against a real MongoDB (CI provides one; see .github/workflows/deploy.yml).
 * Uses a unique creator prefix so it never touches other data, and deletes what it created.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class EventMongoRepositoryTest {

	private static final String PREFIX = "repo-test-" + System.nanoTime() + "-";
	private static final String ALICE = PREFIX + "alice@example.com";
	private static final String BOB = PREFIX + "bob@example.com";
	private static final String ALICE_ID = PREFIX + "1";

	private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");

	@Autowired
	private EventMongoRepository repository;

	private final List<EventDoc> created = new ArrayList<>();

	private EventDoc save(String title, String creatorId, Instant start, String... participantIds) {
		EventDoc e = new EventDoc();
		e.setTitle(title);
		e.setCreatorId(creatorId);
		e.setStartTime(start);
		e.setEndTime(start.plusSeconds(3600));
		e.setParticipantIds(new ArrayList<>(Arrays.asList(participantIds)));
		EventDoc saved = repository.save(e);
		created.add(saved);
		return saved;
	}

	private EventDoc savePublic(String title, Instant start, String tag, String... attendeeIds) {
		EventDoc e = save(title, BOB, start);
		e.setIsPublic(true);
		e.setTags(new ArrayList<>(Arrays.asList(tag)));
		e.setAttendeeIds(new ArrayList<>(Arrays.asList(attendeeIds)));
		return repository.save(e);
	}

	private static Set<String> titles(List<EventDoc> events) {
		return events.stream().map(EventDoc::getTitle).filter(t -> t.startsWith(PREFIX)).collect(Collectors.toSet());
	}

	@AfterEach
	void cleanUp() {
		repository.deleteAll(created);
	}

	@Test
	void returnsOwnAndInvitedEventsInRangeOnly() {
		Instant inRange = Instant.parse("2026-11-15T10:00:00Z");
		save("own", ALICE, inRange);
		save("invited", BOB, inRange, "99", ALICE_ID);
		save("bob private", BOB, inRange, "99");
		save("own but outside range", ALICE, Instant.parse("2027-01-15T10:00:00Z"));

		Set<String> titles = repository.findCreatedOrParticipatingInRange(ALICE, ALICE_ID, START, END).stream()
				.map(EventDoc::getTitle).collect(Collectors.toSet());

		assertEquals(Set.of("own", "invited"), titles);
	}

	@Test
	void attendedPublicEventsShowInTheAttendeesRange() {
		Instant inRange = Instant.parse("2026-11-15T10:00:00Z");
		savePublic(PREFIX + "going", inRange, "#munich", ALICE_ID);
		savePublic(PREFIX + "not going", inRange, "#munich", "99");

		assertEquals(Set.of(PREFIX + "going"),
				titles(repository.findCreatedOrParticipatingInRange(ALICE, ALICE_ID, START, END)));
	}

	@Test
	void feedCandidatesAreUpcomingPublicEventsOptionallyByTag() {
		Instant now = Instant.parse("2026-11-03T12:00:00Z");
		savePublic(PREFIX + "upcoming munich", now.plusSeconds(86400), "#munich");
		savePublic(PREFIX + "upcoming food", now.plusSeconds(86400), "#food");
		savePublic(PREFIX + "already over", now.minusSeconds(86400), "#munich");
		save(PREFIX + "private", BOB, now.plusSeconds(86400));
		PageRequest all = PageRequest.of(0, 1000, Sort.by("startTime"));

		assertEquals(Set.of(PREFIX + "upcoming munich", PREFIX + "upcoming food"),
				titles(repository.findUpcomingPublic(now, all)));
		assertEquals(Set.of(PREFIX + "upcoming munich"),
				titles(repository.findUpcomingPublicWithTag(now, "#munich", all)));
	}

	@Autowired
	private com.planmate.service.EventMongoService eventService;

	@Test
	void answersAreStoredPerInviteeAndDroppedWhenUninvited() {
		EventDoc dinner = save(PREFIX + "dinner", ALICE, Instant.parse("2026-11-15T19:00:00Z"), "2", "3");

		eventService.setResponse(dinner.getId(), "2", "accepted");
		eventService.setResponse(dinner.getId(), "3", "maybe");
		eventService.setResponse(dinner.getId(), "3", "declined");
		assertEquals(java.util.Map.of("2", "accepted", "3", "declined"),
				repository.findById(dinner.getId()).get().getResponses());

		EventDoc update = repository.findById(dinner.getId()).get();
		update.setParticipantIds(new ArrayList<>(Arrays.asList("2")));
		eventService.updateEvent(dinner.getId(), update);
		assertEquals(java.util.Map.of("2", "accepted"), repository.findById(dinner.getId()).get().getResponses());
	}
}
