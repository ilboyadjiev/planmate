package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.planmate.PlanmateApplication;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;

/**
 * Search against a real MongoDB (CI provides one): search words are stored on save, the index exists, prefix
 * queries find events, and guests only find public, upcoming events. Uses made-up words so it cannot collide with
 * other data, and deletes what it created.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class EventSearchIntegrationTest {

	private static final String WORD = "zqx" + Long.toString(System.nanoTime(), 36);
	private static final Instant NOW = Instant.now();

	@Autowired
	private EventMongoRepository repository;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private EventSearchService search;

	private final List<String> created = new ArrayList<>();

	private EventDoc save(String title, boolean isPublic, long startsInHours) {
		EventDoc e = new EventDoc();
		e.setTitle(title);
		e.setCreatorId("search-test@example.com");
		e.setIsPublic(isPublic);
		e.setStartTime(NOW.plusSeconds(startsInHours * 3600));
		e.setEndTime(NOW.plusSeconds(startsInHours * 3600 + 3600));
		EventDoc saved = repository.save(e);
		created.add(saved.getId());
		return saved;
	}

	@AfterEach
	void cleanUp() {
		repository.deleteAllById(created);
	}

	private List<String> titles(String query) {
		return search.search(null, query, 0, 10).getEvents().stream().map(FeedEvent::getTitle)
				.collect(Collectors.toList());
	}

	@Test
	void savingAnEventStoresItsSearchWords() {
		EventDoc saved = save(WORD + " Café night", true, 24);
		EventDoc stored = mongo.findById(saved.getId(), EventDoc.class);
		assertEquals(List.of(WORD, "cafe", "night"), stored.getSearchTerms());

		saved.setTitle(WORD + " brunch");
		repository.save(saved);
		assertEquals(List.of(WORD, "brunch"), mongo.findById(saved.getId(), EventDoc.class).getSearchTerms());
	}

	@Test
	void theSearchWordsAreIndexed() {
		search.prepareIndex();
		assertTrue(mongo.indexOps(EventDoc.class).getIndexInfo().stream()
				.anyMatch(index -> "searchTerms".equals(index.getName())));
	}

	@Test
	void findsPublicUpcomingEventsByTheStartOfAWord() {
		save(WORD + "concert in the park", true, 48);
		save(WORD + "concert private", false, 48);
		save(WORD + "concert over", true, -5);

		assertEquals(List.of(WORD + "concert in the park"), titles(WORD + "conc"));
		assertEquals(1, search.search(null, WORD + "conc", 0, 10).getTotal());
	}

	@Test
	void eventsSavedBeforeSearchExistedGetSearchWords() {
		EventDoc old = save(WORD + " legacy", true, 24);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(old.getId())),
				new org.springframework.data.mongodb.core.query.Update().unset("searchTerms"), EventDoc.class);
		assertTrue(titles(WORD).isEmpty());

		search.prepareIndex();
		assertEquals(List.of(WORD + " legacy"), titles(WORD));
	}
}
