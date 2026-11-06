package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.planmate.dto2.EventDoc;

class EventPreviewServiceTest {

	private final EventPreviewService previews = new EventPreviewService("https://www.planmate.org/", "Europe/Berlin");

	private static EventDoc event(boolean isPublic) {
		EventDoc e = new EventDoc();
		e.setId("abc123");
		e.setTitle("HULLIS - Comedy. Dinner. Show.");
		e.setDescription("Three courses and\n a comedy show.");
		e.setStartTime(Instant.parse("2026-11-14T19:00:00Z"));
		e.setIsPublic(isPublic);
		EventDoc.EventLocation where = new EventDoc.EventLocation();
		where.setName("Lustspielhaus");
		where.setCity("Munich");
		e.setLocation(where);
		return e;
	}

	@Test
	void publicEventsShowTheirDetails() {
		String html = previews.page("abc123", event(true));
		assertTrue(html.contains("<meta property=\"og:title\" content=\"HULLIS - Comedy. Dinner. Show.\">"));
		assertTrue(html.contains("<meta property=\"og:description\" content=\"Sat 14 Nov 2026, 20:00 · Lustspielhaus, Munich. Three courses and a comedy show.\">"));
		assertTrue(html.contains("<meta property=\"og:url\" content=\"https://www.planmate.org/events/abc123\">"));
		assertTrue(html.contains("<meta property=\"og:image\" content=\"https://www.planmate.org/og-image.png\">"));
		assertTrue(html.contains("<meta property=\"og:image:width\" content=\"1200\">"));
		assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary_large_image\">"));
	}

	@Test
	void privateCancelledAndUnknownEventsRevealNothing() {
		EventDoc cancelled = event(true);
		cancelled.setCancelled(true);
		for (EventDoc e : new EventDoc[] { event(false), cancelled, null }) {
			String html = previews.page("abc123", e);
			assertFalse(html.contains("HULLIS"));
			assertFalse(html.contains("Lustspielhaus"));
			assertTrue(html.contains("content=\"" + EventPreviewService.GENERIC_TITLE + "\""));
		}
	}

	@Test
	void escapesWhatTheHostWrote() {
		EventDoc e = event(true);
		e.setTitle("\"><script>alert(1)</script>");
		String html = previews.page("abc123", e);
		assertFalse(html.contains("<script>"));
		assertTrue(html.contains("&quot;&gt;&lt;script&gt;"));
	}

	@Test
	void oddIdsLinkToTheStartPage() {
		String html = previews.page("../x\"y", null);
		assertTrue(html.contains("<meta property=\"og:url\" content=\"https://www.planmate.org/\">"));
	}

	@Test
	void longDescriptionsAreCutAtAWord() {
		String cut = EventPreviewService.shorten("word ".repeat(100).trim(), 50);
		assertTrue(cut.length() <= 50);
		assertTrue(cut.endsWith("word…"));
		assertEquals("short", EventPreviewService.shorten("short", 50));
	}

	@Test
	void withoutADescriptionTheDateAndPlaceRemain() {
		EventDoc e = event(true);
		e.setDescription(null);
		assertEquals("Sat 14 Nov 2026, 20:00 · Lustspielhaus, Munich", previews.description(e));
		e.setStartTime(null);
		e.setLocation(null);
		assertEquals(EventPreviewService.GENERIC_DESCRIPTION, previews.description(e));
	}
}
