package com.planmate.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.planmate.dto2.EventDoc;
import com.planmate.service.CalendarExportService;

class IcsCalendarTest {

	private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

	private static EventDoc jazz() {
		EventDoc e = new EventDoc();
		e.setId("abc123");
		e.setTitle("Jazz, wine; and \\\\ more");
		e.setDescription("Live jazz.\nBring friends.");
		e.setStartTime(Instant.parse("2026-11-07T18:00:00Z"));
		e.setEndTime(Instant.parse("2026-11-07T22:00:00Z"));
		EventDoc.EventLocation where = new EventDoc.EventLocation();
		where.setName("Jazzclub Unterfahrt");
		where.setStreet("Einsteinstraße 42");
		where.setZipcode("81675");
		where.setCity("Munich");
		e.setLocation(where);
		return e;
	}

	private static String ics(IcsCalendar.Entry... entries) {
		return IcsCalendar.write(Arrays.asList(entries), "PlanMate", "https://www.planmate.org/", NOW);
	}

	/** Undoes line folding, as calendar apps do. */
	private static String unfolded(String ics) {
		return ics.replace("\r\n ", "");
	}

	@Test
	void writesAStandardCalendarWithTheEventInUtc() {
		String ics = ics(new IcsCalendar.Entry(jazz(), IcsCalendar.Status.CONFIRMED));
		assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
		assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
		assertFalse(ics.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
		String text = unfolded(ics);
		assertTrue(text.contains("X-WR-CALNAME:PlanMate\r\n"));
		assertTrue(text.contains("UID:abc123@planmate.org\r\n"), "the same UID every time, so apps update instead of copying");
		assertTrue(text.contains("DTSTART:20261107T180000Z\r\n"));
		assertTrue(text.contains("DTEND:20261107T220000Z\r\n"));
		assertTrue(text.contains("DTSTAMP:20261105T100000Z\r\n"));
		assertTrue(text.contains("STATUS:CONFIRMED\r\n"));
		assertTrue(text.contains("URL:https://www.planmate.org/events/abc123\r\n"));
		assertTrue(text.contains("LOCATION:Jazzclub Unterfahrt\\, Einsteinstraße 42\\, 81675 Munich\r\n"));
	}

	@Test
	void escapesTextAndKeepsLineBreaks() {
		String text = unfolded(ics(new IcsCalendar.Entry(jazz(), IcsCalendar.Status.TENTATIVE)));
		assertTrue(text.contains("SUMMARY:Jazz\\, wine\\; and \\\\\\\\ more\r\n"), text);
		assertTrue(text.contains("DESCRIPTION:Live jazz.\\nBring friends.\\n\\nOn PlanMate: https://www.planmate.org/events/abc123\r\n"));
		assertTrue(text.contains("STATUS:TENTATIVE\r\n"));
	}

	@Test
	void foldsLongLinesAtSeventyFiveBytesWithoutBreakingCharacters() {
		EventDoc e = jazz();
		e.setDescription("Glühwein und Lebkuchen am Münchner Christkindlmarkt – überall Lichter, Musik und Gäste. ".repeat(4));
		String ics = ics(new IcsCalendar.Entry(e, IcsCalendar.Status.CONFIRMED));
		for (String line : ics.split("\r\n")) {
			assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
		}
		assertTrue(unfolded(ics).contains("Glühwein und Lebkuchen am Münchner Christkindlmarkt – überall Lichter"));
	}

	@Test
	void eventsWithoutAnEndLastAnHourAndUntitledOnesGetAName() {
		EventDoc e = jazz();
		e.setTitle(null);
		e.setEndTime(null);
		e.setLocation(null);
		String text = unfolded(ics(new IcsCalendar.Entry(e, IcsCalendar.Status.CONFIRMED)));
		assertTrue(text.contains("DTEND:20261107T190000Z\r\n"));
		assertTrue(text.contains("SUMMARY:PlanMate event\r\n"));
		assertFalse(text.contains("LOCATION:"));
	}

	@Test
	void aSingleEventDownloadHasNoSubscriptionName() {
		String ics = IcsCalendar.write(Collections.singletonList(new IcsCalendar.Entry(jazz(), IcsCalendar.Status.CONFIRMED)),
				null, "https://www.planmate.org", NOW);
		assertFalse(ics.contains("X-WR-CALNAME"));
		assertFalse(ics.contains("REFRESH-INTERVAL"));
	}

	@Test
	void fileNamesAreReadable() {
		EventDoc e = jazz();
		e.setTitle("Münchner Christkindlmarkt!");
		assertEquals("munchner-christkindlmarkt.ics", CalendarExportService.fileName(e));
		e.setTitle("???");
		assertEquals("planmate-event.ics", CalendarExportService.fileName(e));
	}
}
