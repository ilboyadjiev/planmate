package com.planmate.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.planmate.dto2.EventDoc;

/**
 * The page link previews are made from: what Facebook, Messenger, Instagram, WhatsApp, iMessage, Slack, Discord and
 * other apps show when someone pastes a link to an event (/events/{id}). Their crawlers do not run JavaScript, so
 * nginx sends them here instead of to the React app (see nginx.conf); people still get the app.
 *
 * Only public events show their title, date, place and description. Private events, cancelled ones and unknown
 * links get a plain PlanMate preview, so a shared link never reveals more than the event page itself would to
 * someone who is not logged in.
 */
@Service
public class EventPreviewService {

	/** Facebook shows about 2 lines of the description; WhatsApp and iMessage even less. */
	static final int MAX_DESCRIPTION = 200;
	static final String SITE_NAME = "PlanMate";
	static final String GENERIC_TITLE = "An event on PlanMate";
	static final String GENERIC_DESCRIPTION = "Open the link to see the details and who's going.";
	/** 1200 x 630, the size Facebook asks for (frontend: public/og-image.png). */
	static final String IMAGE_PATH = "/og-image.png";
	static final int IMAGE_WIDTH = 1200;
	static final int IMAGE_HEIGHT = 630;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

	private final String siteUrl;
	private final ZoneId zone;

	public EventPreviewService(@Value("${app.public-url:https://www.planmate.org}") String siteUrl,
			@Value("${app.preview.time-zone:Europe/Berlin}") String zone) {
		this.siteUrl = StringUtils.removeEnd(siteUrl, "/");
		this.zone = ZoneId.of(zone);
	}

	/** The preview page for an event link; event is null when there is no such event. */
	public String page(String id, EventDoc event) {
		String url = linkTo(id);
		boolean shown = event != null && Boolean.TRUE.equals(event.getIsPublic())
				&& !Boolean.TRUE.equals(event.getCancelled()) && StringUtils.isNotBlank(event.getTitle());
		String title = shown ? StringUtils.normalizeSpace(event.getTitle()) : GENERIC_TITLE;
		String description = shown ? description(event) : GENERIC_DESCRIPTION;

		StringBuilder html = new StringBuilder(2048);
		html.append("<!DOCTYPE html>\n<html lang=\"en\" prefix=\"og: https://ogp.me/ns#\">\n<head>\n")
				.append("<meta charset=\"utf-8\">\n")
				.append("<title>").append(escape(title)).append(" · PlanMate</title>\n");
		meta(html, "name", "description", description);
		meta(html, "property", "og:site_name", SITE_NAME);
		meta(html, "property", "og:type", "website");
		meta(html, "property", "og:url", url);
		meta(html, "property", "og:title", title);
		meta(html, "property", "og:description", description);
		meta(html, "property", "og:image", siteUrl + IMAGE_PATH);
		meta(html, "property", "og:image:width", String.valueOf(IMAGE_WIDTH));
		meta(html, "property", "og:image:height", String.valueOf(IMAGE_HEIGHT));
		meta(html, "property", "og:image:alt", "PlanMate");
		meta(html, "property", "og:locale", "en_US");
		meta(html, "name", "twitter:card", "summary_large_image");
		meta(html, "name", "twitter:title", title);
		meta(html, "name", "twitter:description", description);
		meta(html, "name", "twitter:image", siteUrl + IMAGE_PATH);
		html.append("<link rel=\"canonical\" href=\"").append(escape(url)).append("\">\n")
				.append("</head>\n<body>\n")
				.append("<h1>").append(escape(title)).append("</h1>\n")
				.append("<p>").append(escape(description)).append("</p>\n")
				.append("<p><a href=\"").append(escape(url)).append("\">Open in PlanMate</a></p>\n")
				.append("</body>\n</html>\n");
		return html.toString();
	}

	/** "Sat 14 Nov 2026, 20:00 · Lustspielhaus, Munich. The show starts ..." */
	String description(EventDoc event) {
		String when = when(event.getStartTime());
		String where = place(event.getLocation());
		String head = Stream.of(when, where).filter(Objects::nonNull).collect(Collectors.joining(" · "));
		String text = StringUtils.normalizeSpace(event.getDescription());
		if (StringUtils.isEmpty(text)) {
			return head.isEmpty() ? GENERIC_DESCRIPTION : head;
		}
		return shorten(head.isEmpty() ? text : head + ". " + text, MAX_DESCRIPTION);
	}

	String when(Instant start) {
		if (start == null) {
			return null;
		}
		return DAY.format(start.atZone(zone)) + ", " + TIME.format(start.atZone(zone));
	}

	/** The venue and its city ("Lustspielhaus, Munich"), or whatever of them is known. */
	static String place(EventDoc.EventLocation location) {
		if (location == null) {
			return null;
		}
		String venue = StringUtils.firstNonBlank(location.getName(), location.getStreet());
		String joined = Stream.of(venue, location.getCity())
				.map(StringUtils::normalizeSpace).filter(StringUtils::isNotEmpty).distinct()
				.collect(Collectors.joining(", "));
		return joined.isEmpty() ? null : joined;
	}

	/** Cut at a word, with an ellipsis, so the apps do not cut mid-word themselves. */
	static String shorten(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		String cut = text.substring(0, max - 1);
		int space = cut.lastIndexOf(' ');
		if (space > max / 2) {
			cut = cut.substring(0, space);
		}
		return StringUtils.stripEnd(cut, " ,.;:-") + "…";
	}

	private static void meta(StringBuilder html, String attribute, String name, String content) {
		html.append("<meta ").append(attribute).append("=\"").append(name).append("\" content=\"")
				.append(escape(content)).append("\">\n");
	}

	private static String escape(String value) {
		return HtmlUtils.htmlEscape(value, "UTF-8");
	}

	/** The event's page; for an id that cannot be one, the app's start page. */
	private String linkTo(String id) {
		return id != null && id.matches("[A-Za-z0-9_-]{1,64}") ? siteUrl + "/events/" + id : siteUrl + "/";
	}
}
