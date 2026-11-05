package com.planmate.discovery;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.service.CityTagService;

/**
 * Applies discovery messages to the events collection, upserting by event id:
 *
 * - a message older than (or as old as) the version already applied is skipped;
 * - the event is public, hosted by the PlanMate Picks account (events@planmate.org), and its description ends with
 *   "Source: <name>";
 * - EventCancelled marks it cancelled; a later EventUpdated un-cancels it;
 * - who is invited, going or has answered is never touched.
 *
 * Messages that make no sense throw InvalidDiscoveryMessageException, which sends them to the dead-letter topic.
 */
@Service
public class DiscoveryEventImporter {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryEventImporter.class);
	private static final Set<String> TYPES = Set.of(DiscoveryMessage.DISCOVERED, DiscoveryMessage.UPDATED,
			DiscoveryMessage.CANCELLED);
	/** Discovery ids: a source prefix and the source's own id ("tm-G5v0Z9...", "curated-steam-sale"). */
	private static final Pattern EVENT_ID = Pattern.compile("[a-z0-9]{1,20}-[A-Za-z0-9._-]{1,150}");
	static final int MAX_TITLE = 300;
	static final int MAX_DESCRIPTION = 4000;

	public enum Result {
		CREATED, UPDATED, CANCELLED, SKIPPED_OLDER, SKIPPED_UNKNOWN
	}

	private final EventMongoRepository events;
	private final CityTagService cityTags;
	private final String creator;

	public DiscoveryEventImporter(EventMongoRepository events, CityTagService cityTags,
			@Value("${discovery.events-creator:events@planmate.org}") String creator) {
		this.events = events;
		this.cityTags = cityTags;
		this.creator = creator;
	}

	public Result apply(DiscoveryMessage message) {
		validate(message);
		DiscoveryMessage.Event e = message.getEvent();
		EventDoc existing = events.findById(message.getEventId()).orElse(null);
		boolean cancelled = DiscoveryMessage.CANCELLED.equals(message.getType());

		if (existing != null && existing.getDiscoverySource() == null) {
			throw new InvalidDiscoveryMessageException("Event " + message.getEventId() + " exists but was not made by discovery");
		}
		if (existing != null && existing.getDiscoveryVersion() != null && existing.getDiscoveryVersion() >= message.getVersion()) {
			log.debug("Skipping {} v{}: v{} is already applied", message.getEventId(), message.getVersion(), existing.getDiscoveryVersion());
			return Result.SKIPPED_OLDER;
		}
		if (existing == null && cancelled) {
			return Result.SKIPPED_UNKNOWN; // never shown, nothing to cancel
		}

		EventDoc doc = existing != null ? existing : new EventDoc();
		String previousCityTag = doc.getCityTag();
		doc.setId(message.getEventId());
		doc.setTitle(StringUtils.abbreviate(e.getTitle().trim(), MAX_TITLE));
		doc.setDescription(description(e));
		doc.setStartTime(e.getStartTime());
		doc.setEndTime(e.getEndTime());
		doc.setLocation(e.getLocation());
		doc.setTags(tags(e.getTags()));
		doc.setIsPublic(true);
		doc.setCreatorId(creator);
		doc.setCancelled(cancelled ? Boolean.TRUE : null);
		doc.setSourceUrl(webLink(e.getSourceUrl()));
		doc.setDiscoverySource(e.getSource());
		doc.setDiscoveryVersion(message.getVersion());
		cityTags.apply(doc, previousCityTag);
		events.save(doc);

		Result result = existing == null ? Result.CREATED : cancelled ? Result.CANCELLED : Result.UPDATED;
		log.info("Discovery {} {} v{} ({})", result.name().toLowerCase(), message.getEventId(), message.getVersion(), doc.getTitle());
		return result;
	}

	/** The written description, then "Source: Ticketmaster". */
	static String description(DiscoveryMessage.Event e) {
		String source = "Source: " + e.getSourceName().trim();
		String body = StringUtils.trimToNull(e.getDescription());
		if (body == null) {
			return source;
		}
		return StringUtils.abbreviate(body, MAX_DESCRIPTION - source.length() - 2) + "\n\n" + source;
	}

	/** The source's page, only as a plain http(s) link (the app shows it as "Read more here"); anything else is dropped. */
	static String webLink(String url) {
		String trimmed = StringUtils.trimToNull(url);
		if (trimmed == null || trimmed.length() > 2000) {
			return null;
		}
		try {
			java.net.URI uri = new java.net.URI(trimmed);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
			return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null ? trimmed : null;
		} catch (java.net.URISyntaxException ex) {
			return null;
		}
	}

	private static List<String> tags(List<String> tags) {
		Set<String> clean = new LinkedHashSet<>();
		if (tags != null) {
			for (String tag : tags) {
				String t = tag == null ? "" : tag.trim().toLowerCase().replaceAll("\\s+", "");
				if (t.length() > 1 && t.length() <= 40) {
					clean.add(t.startsWith("#") ? t : "#" + t);
				}
			}
		}
		return new ArrayList<>(clean);
	}

	static void validate(DiscoveryMessage m) {
		if (m == null || !TYPES.contains(m.getType())) {
			throw new InvalidDiscoveryMessageException("Unknown message type: " + (m == null ? null : m.getType()));
		}
		if (m.getEventId() == null || !EVENT_ID.matcher(m.getEventId()).matches()) {
			throw new InvalidDiscoveryMessageException("Not a discovery event id: " + m.getEventId());
		}
		if (m.getVersion() < 1) {
			throw new InvalidDiscoveryMessageException("Version must be 1 or more: " + m.getVersion());
		}
		DiscoveryMessage.Event e = m.getEvent();
		if (e == null || !m.getEventId().equals(e.getId())) {
			throw new InvalidDiscoveryMessageException("The event is missing or has another id than the message");
		}
		if (StringUtils.isBlank(e.getTitle()) || e.getStartTime() == null || StringUtils.isBlank(e.getSourceName())
				|| StringUtils.isBlank(e.getSource())) {
			throw new InvalidDiscoveryMessageException("The event needs a title, a start and its source");
		}
		if (e.getEndTime() != null && !e.getEndTime().isAfter(e.getStartTime())) {
			throw new InvalidDiscoveryMessageException("The event ends before it starts");
		}
	}
}
