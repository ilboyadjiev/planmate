package com.planmate.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.planmate.PlanmateApplication;
import com.planmate.dto2.EventDoc;

/**
 * Runs the cleanup against a real MongoDB (CI provides one; see .github/workflows/deploy.yml). Every document uses a
 * unique creator prefix so it never touches other data, and the test deletes what it created.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class DiscoveryEventCleanupTest {

	private static final String PREFIX = "cleanup-test-" + System.nanoTime() + "-";
	private static final String PICKS = PREFIX + "events@planmate.org";
	private static final String SOMEONE_ELSE = PREFIX + "alice@example.com";

	/** A fixed "now" so the fixtures' ages are stable. */
	private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
	private static final Instant LONG_AGO = NOW.minus(40, ChronoUnit.DAYS);
	private static final Instant RECENTLY = NOW.minus(5, ChronoUnit.DAYS);

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private DiscoveryEventCleanup cleanup;

	private final List<EventDoc> created = new ArrayList<>();

	@AfterEach
	void tidy() {
		mongo.remove(Query.query(Criteria.where("creatorId").in(PICKS, SOMEONE_ELSE)), EventDoc.class);
	}

	private EventDoc save(String title, String creator, Instant start, Instant end, List<String> attendees,
			List<String> maybes) {
		EventDoc e = new EventDoc();
		e.setTitle(PREFIX + title);
		e.setCreatorId(creator);
		e.setStartTime(start);
		e.setEndTime(end);
		e.setAttendeeIds(new ArrayList<>(attendees));
		e.setMaybeIds(new ArrayList<>(maybes));
		EventDoc saved = mongo.save(e);
		created.add(saved);
		return saved;
	}

	private Set<String> remainingTitles() {
		return mongo.find(Query.query(Criteria.where("creatorId").in(PICKS, SOMEONE_ELSE)), EventDoc.class)
				.stream().map(EventDoc::getTitle).collect(Collectors.toSet());
	}

	@Test
	void removesOnlyLongPastPicksEventsThatNobodyJoined() {
		List<String> none = List.of();
		EventDoc stale = save("stale", PICKS, LONG_AGO, LONG_AGO.plusSeconds(3600), none, none);
		save("stale-no-end", PICKS, LONG_AGO, null, none, none); // web-feed import: counts by its start
		save("joined", PICKS, LONG_AGO, LONG_AGO.plusSeconds(3600), List.of("7"), none); // someone's history
		save("maybe", PICKS, LONG_AGO, LONG_AGO.plusSeconds(3600), none, List.of("7")); // someone's history
		save("recent", PICKS, RECENTLY, RECENTLY.plusSeconds(3600), none, none); // not old enough
		save("upcoming-no-end", PICKS, RECENTLY, null, none, none); // start not past the cutoff
		save("someone-else", SOMEONE_ELSE, LONG_AGO, LONG_AGO.plusSeconds(3600), none, none); // not the Picks account

		long removed = cleanup.prune(PICKS, 30, NOW);

		assertThat(removed).isEqualTo(2);
		assertThat(mongo.findById(stale.getId(), EventDoc.class)).isNull();
		assertThat(remainingTitles()).containsExactlyInAnyOrder(
				PREFIX + "joined", PREFIX + "maybe", PREFIX + "recent", PREFIX + "upcoming-no-end",
				PREFIX + "someone-else");
	}

	@Test
	void removesNothingWhenEverythingIsRecentOrHeldByAttendees() {
		List<String> none = List.of();
		save("recent", PICKS, RECENTLY, RECENTLY.plusSeconds(3600), none, none);
		save("joined", PICKS, LONG_AGO, LONG_AGO.plusSeconds(3600), List.of("7"), none);

		long removed = cleanup.prune(PICKS, 30, NOW);

		assertThat(removed).isZero();
		assertThat(remainingTitles()).containsExactlyInAnyOrder(PREFIX + "recent", PREFIX + "joined");
	}
}
