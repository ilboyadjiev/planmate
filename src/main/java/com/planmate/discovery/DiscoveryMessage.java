package com.planmate.discovery;

import java.time.Instant;
import java.util.List;

import com.planmate.dto2.EventDoc;

/**
 * A message from the discovery service on discovery.events (keyed by eventId).
 *
 * type: EventDiscovered, EventUpdated or EventCancelled. version: grows with every message for the event, so an
 * older message than the one already applied is ignored. event: always the full event.
 */
public class DiscoveryMessage {

	public static final String DISCOVERED = "EventDiscovered";
	public static final String UPDATED = "EventUpdated";
	public static final String CANCELLED = "EventCancelled";

	private String type;
	private String eventId;
	private long version;
	private Instant occurredAt;
	private Event event;

	/** The event in PlanMate's terms; description already written (tips included), without the Source line. */
	public static class Event {
		private String id;
		private String title;
		private String description;
		private Instant startTime;
		private Instant endTime;
		private EventDoc.EventLocation location;
		private List<String> tags;
		private String source;
		private String sourceName;
		private String sourceUrl;
		private boolean cancelled;

		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
		public String getTitle() { return title; }
		public void setTitle(String title) { this.title = title; }
		public String getDescription() { return description; }
		public void setDescription(String description) { this.description = description; }
		public Instant getStartTime() { return startTime; }
		public void setStartTime(Instant startTime) { this.startTime = startTime; }
		public Instant getEndTime() { return endTime; }
		public void setEndTime(Instant endTime) { this.endTime = endTime; }
		public EventDoc.EventLocation getLocation() { return location; }
		public void setLocation(EventDoc.EventLocation location) { this.location = location; }
		public List<String> getTags() { return tags; }
		public void setTags(List<String> tags) { this.tags = tags; }
		public String getSource() { return source; }
		public void setSource(String source) { this.source = source; }
		public String getSourceName() { return sourceName; }
		public void setSourceName(String sourceName) { this.sourceName = sourceName; }
		public String getSourceUrl() { return sourceUrl; }
		public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
		public boolean isCancelled() { return cancelled; }
		public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
	}

	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public String getEventId() { return eventId; }
	public void setEventId(String eventId) { this.eventId = eventId; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
	public Instant getOccurredAt() { return occurredAt; }
	public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
	public Event getEvent() { return event; }
	public void setEvent(Event event) { this.event = event; }
}
