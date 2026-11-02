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

	private void save(String title, String creatorId, Instant start, String... participantIds) {
		EventDoc e = new EventDoc();
		e.setTitle(title);
		e.setCreatorId(creatorId);
		e.setStartTime(start);
		e.setEndTime(start.plusSeconds(3600));
		e.setParticipantIds(new ArrayList<>(Arrays.asList(participantIds)));
		created.add(repository.save(e));
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
}
