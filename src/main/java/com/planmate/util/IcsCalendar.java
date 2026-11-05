package com.planmate.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.planmate.dto2.EventDoc;

/**
 * Writes events in the iCalendar format (RFC 5545, .ics), which Apple Calendar, Google Calendar, Outlook, Samsung
 * Calendar and practically every other calendar app can import or subscribe to.
 *
 * Times are written in UTC, so every app shows them in its own time zone. Each event keeps the same UID across
 * downloads ({id}@planmate.org), so importing it again or refreshing a subscription updates it instead of adding a
 * copy.
 */
public final class IcsCalendar {

	/** How the viewer stands to an event, which decides how it is marked in their calendar. */
	public enum Status {
		CONFIRMED, TENTATIVE
	}

	/** One event and how it should be marked. */
	public static final class Entry {
		final EventDoc event;
		final Status status;

		public Entry(EventDoc event, Status status) {
			this.event = event;
			this.status = status;
		}
	}

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);
	private static final String CRLF = "\r\n";
	private static final int MAX_LINE_OCTETS = 75;

	private IcsCalendar() {
	}

	/**
	 * A calendar file. name: shown by subscribing apps ("PlanMate"); null for a single-event download.
	 * siteUrl: the public address of the app, for links back to each event's page.
	 */
	public static String write(List<Entry> entries, String name, String siteUrl, Instant now) {
		StringBuilder ics = new StringBuilder();
		line(ics, "BEGIN:VCALENDAR");
		line(ics, "VERSION:2.0");
		line(ics, "PRODID:-//PlanMate//PlanMate Calendar//EN");
		line(ics, "CALSCALE:GREGORIAN");
		line(ics, "METHOD:PUBLISH");
		if (name != null) {
			line(ics, "X-WR-CALNAME:" + text(name));
			// Hints for subscribing apps to check for changes every hour (most honour them, some refresh less often)
			line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
			line(ics, "X-PUBLISHED-TTL:PT1H");
		}
		for (Entry entry : entries) {
			event(ics, entry, siteUrl, now);
		}
		line(ics, "END:VCALENDAR");
		return ics.toString();
	}

	private static void event(StringBuilder ics, Entry entry, String siteUrl, Instant now) {
		EventDoc e = entry.event;
		if (e.getId() == null || e.getStartTime() == null) {
			return;
		}
		Instant end = e.getEndTime() != null && e.getEndTime().isAfter(e.getStartTime()) ? e.getEndTime()
				: e.getStartTime().plusSeconds(3600);
		String page = StringUtils.removeEnd(siteUrl, "/") + "/events/" + e.getId();

		line(ics, "BEGIN:VEVENT");
		line(ics, "UID:" + e.getId() + "@planmate.org");
		line(ics, "DTSTAMP:" + UTC.format(now));
		line(ics, "DTSTART:" + UTC.format(e.getStartTime()));
		line(ics, "DTEND:" + UTC.format(end));
		line(ics, "SUMMARY:" + text(StringUtils.defaultIfBlank(e.getTitle(), "PlanMate event")));
		String description = Stream.of(StringUtils.trimToNull(e.getDescription()), "On PlanMate: " + page)
				.filter(StringUtils::isNotEmpty).collect(Collectors.joining("\n\n"));
		line(ics, "DESCRIPTION:" + text(description));
		String location = location(e.getLocation());
		if (location != null) {
			line(ics, "LOCATION:" + text(location));
		}
		line(ics, "URL:" + page);
		line(ics, "STATUS:" + entry.status.name());
		line(ics, "END:VEVENT");
	}

	/** "Jazzclub Unterfahrt, Einsteinstraße 42, 81675 Munich". */
	static String location(EventDoc.EventLocation location) {
		if (location == null) {
			return null;
		}
		String cityLine = StringUtils.trimToNull(StringUtils.normalizeSpace(
				StringUtils.defaultString(location.getZipcode()) + " " + StringUtils.defaultString(location.getCity())));
		String joined = Stream.of(location.getName(), location.getStreet(), cityLine, location.getCountry())
				.map(StringUtils::trimToNull).filter(StringUtils::isNotEmpty).distinct()
				.collect(Collectors.joining(", "));
		return joined.isEmpty() ? null : joined;
	}

	/** Escapes a text value: backslash, semicolon, comma and line breaks (RFC 5545, 3.3.11). */
	static String text(String value) {
		return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
				.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
	}

	/** Adds a content line, folded at 75 octets without splitting a character (RFC 5545, 3.1). */
	static void line(StringBuilder ics, String content) {
		int octets = 0;
		int i = 0;
		while (i < content.length()) {
			int codePoint = content.codePointAt(i);
			String ch = new String(Character.toChars(codePoint));
			int size = ch.getBytes(StandardCharsets.UTF_8).length;
			if (octets + size > MAX_LINE_OCTETS) {
				ics.append(CRLF).append(' ');
				octets = 1;
			}
			ics.append(ch);
			octets += size;
			i += Character.charCount(codePoint);
		}
		ics.append(CRLF);
	}
}
