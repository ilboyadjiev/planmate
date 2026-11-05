package com.planmate.util;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.planmate.dto2.EventDoc;

/**
 * Events that are dates of the same show: the same host, the same title and the same venue (e.g. "HULLIS - Comedy.
 * Dinner. Show." at the same theatre, listed once per evening). Case and spacing do not matter.
 */
public final class EventSeries {

	private EventSeries() {
	}

	/** The series an event belongs to, or null when it has no title. */
	public static String key(EventDoc event) {
		String title = normalize(event.getTitle());
		if (title.isEmpty()) {
			return null;
		}
		EventDoc.EventLocation where = event.getLocation();
		String venue = where == null ? ""
				: normalize(StringUtils.firstNonBlank(where.getName(), where.getStreet(), where.getCity()));
		return normalize(event.getCreatorId()) + "|" + title + "|" + venue;
	}

	private static String normalize(String text) {
		return StringUtils.normalizeSpace(StringUtils.defaultString(text)).toLowerCase(Locale.ROOT);
	}
}
