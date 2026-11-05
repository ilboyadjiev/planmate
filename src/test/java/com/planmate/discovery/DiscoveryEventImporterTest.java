package com.planmate.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.service.CityTagService;

class DiscoveryEventImporterTest {

	private EventMongoRepository repository;
	private DiscoveryEventImporter importer;
	private final Map<String, EventDoc> stored = new HashMap<>();

	@BeforeEach
	void setUp() {
		repository = mock(EventMongoRepository.class);
		when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get((String) inv.getArgument(0))));
		when(repository.save(any())).thenAnswer(inv -> {
			EventDoc doc = inv.getArgument(0);
			stored.put(doc.getId(), doc);
			return doc;
		});
		importer = new DiscoveryEventImporter(repository, new CityTagService(), "events@planmate.org");
	}

	static DiscoveryMessage message(String type, long version) {
		DiscoveryMessage.Event e = new DiscoveryMessage.Event();
		e.setId("tm-ABC123");
		e.setTitle("Jazz Night");
		e.setDescription("Live jazz in a cellar club.\n\nTip: Book ahead");
		e.setStartTime(Instant.parse("2026-11-20T18:00:00Z"));
		EventDoc.EventLocation where = new EventDoc.EventLocation();
		where.setName("Unterfahrt");
		where.setCity("München");
		e.setLocation(where);
		e.setTags(Arrays.asList("#concert", "#Culture"));
		e.setSource("tm");
		e.setSourceName("Ticketmaster");
		e.setSourceUrl("https://tickets.example/jazz");
		e.setCancelled(DiscoveryMessage.CANCELLED.equals(type));
		DiscoveryMessage m = new DiscoveryMessage();
		m.setType(type);
		m.setEventId("tm-ABC123");
		m.setVersion(version);
		m.setEvent(e);
		return m;
	}

	@Test
	void aNewEventIsPublicHostedByPlanMatePicksAndCreditsItsSource() {
		assertEquals(DiscoveryEventImporter.Result.CREATED, importer.apply(message(DiscoveryMessage.DISCOVERED, 1)));
		EventDoc doc = stored.get("tm-ABC123");
		assertEquals("Jazz Night", doc.getTitle());
		assertEquals("Live jazz in a cellar club.\n\nTip: Book ahead\n\nSource: Ticketmaster", doc.getDescription());
		assertEquals(Boolean.TRUE, doc.getIsPublic());
		assertEquals("events@planmate.org", doc.getCreatorId());
		assertNull(doc.getCancelled());
		assertEquals(Long.valueOf(1), doc.getDiscoveryVersion());
		assertTrue(doc.getTags().containsAll(Arrays.asList("#concert", "#culture", "#munich")), "city tag added: " + doc.getTags());
		assertTrue(doc.getParticipantIds().isEmpty() && doc.getAttendeeIds().isEmpty());
	}

	@Test
	void updatesNeverTouchWhoIsInvitedOrGoing() {
		importer.apply(message(DiscoveryMessage.DISCOVERED, 1));
		EventDoc doc = stored.get("tm-ABC123");
		doc.setAttendeeIds(new ArrayList<>(List.of("7", "8")));
		doc.setParticipantIds(new ArrayList<>(List.of("9")));
		doc.getResponses().put("9", "accepted");

		DiscoveryMessage moved = message(DiscoveryMessage.UPDATED, 2);
		moved.getEvent().setStartTime(Instant.parse("2026-11-20T19:00:00Z"));
		assertEquals(DiscoveryEventImporter.Result.UPDATED, importer.apply(moved));

		EventDoc updated = stored.get("tm-ABC123");
		assertEquals(Instant.parse("2026-11-20T19:00:00Z"), updated.getStartTime());
		assertEquals(List.of("7", "8"), updated.getAttendeeIds());
		assertEquals(List.of("9"), updated.getParticipantIds());
		assertEquals("accepted", updated.getResponses().get("9"));
	}

	@Test
	void olderOrRepeatedVersionsAreSkipped() {
		importer.apply(message(DiscoveryMessage.UPDATED, 5));
		DiscoveryMessage older = message(DiscoveryMessage.UPDATED, 4);
		older.getEvent().setTitle("Old title");
		assertEquals(DiscoveryEventImporter.Result.SKIPPED_OLDER, importer.apply(older));
		assertEquals(DiscoveryEventImporter.Result.SKIPPED_OLDER, importer.apply(message(DiscoveryMessage.UPDATED, 5)));
		assertEquals("Jazz Night", stored.get("tm-ABC123").getTitle());
	}

	@Test
	void cancellingMarksTheEventAndALaterUpdateBringsItBack() {
		importer.apply(message(DiscoveryMessage.DISCOVERED, 1));
		assertEquals(DiscoveryEventImporter.Result.CANCELLED, importer.apply(message(DiscoveryMessage.CANCELLED, 2)));
		assertEquals(Boolean.TRUE, stored.get("tm-ABC123").getCancelled());
		importer.apply(message(DiscoveryMessage.UPDATED, 3));
		assertNull(stored.get("tm-ABC123").getCancelled());
	}

	@Test
	void aCancellationForAnEventNeverSeenChangesNothing() {
		assertEquals(DiscoveryEventImporter.Result.SKIPPED_UNKNOWN, importer.apply(message(DiscoveryMessage.CANCELLED, 3)));
		verify(repository, never()).save(any());
	}

	@Test
	void anEventWithoutTextStillNamesItsSource() {
		DiscoveryMessage m = message(DiscoveryMessage.DISCOVERED, 1);
		m.getEvent().setDescription("  ");
		importer.apply(m);
		assertEquals("Source: Ticketmaster", stored.get("tm-ABC123").getDescription());
	}

	@Test
	void messagesThatMakeNoSenseAreRefused() {
		DiscoveryMessage badType = message("EventExploded", 1);
		DiscoveryMessage badId = message(DiscoveryMessage.DISCOVERED, 1);
		badId.setEventId("../etc");
		DiscoveryMessage otherId = message(DiscoveryMessage.DISCOVERED, 1);
		otherId.getEvent().setId("tm-OTHER");
		DiscoveryMessage noStart = message(DiscoveryMessage.DISCOVERED, 1);
		noStart.getEvent().setStartTime(null);
		DiscoveryMessage noVersion = message(DiscoveryMessage.DISCOVERED, 0);
		for (DiscoveryMessage m : List.of(badType, badId, otherId, noStart, noVersion)) {
			assertThrows(InvalidDiscoveryMessageException.class, () -> importer.apply(m));
		}
		verify(repository, never()).save(any());
	}

	@Test
	void anEventPeopleMadeThemselvesIsNeverOverwritten() {
		EventDoc own = new EventDoc();
		own.setId("tm-ABC123");
		own.setCreatorId("alice@example.com");
		stored.put("tm-ABC123", own);
		assertThrows(InvalidDiscoveryMessageException.class, () -> importer.apply(message(DiscoveryMessage.DISCOVERED, 1)));
		assertEquals("alice@example.com", stored.get("tm-ABC123").getCreatorId());
	}
}
