package com.planmate.dto2;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "events")
@Data
public class EventDoc {

	@Id
	private String id;

	private String title;
	private String description;
	private Instant startTime;
	private Instant endTime;

	private String creatorId;

	private List<String> participantIds = new ArrayList<>();
	/**
	 * Friend groups (Postgres friend_group ids, as strings) invited as a whole. Their members were added to
	 * participantIds when the event was saved; this keeps which groups were invited, e.g. for the Discover feed.
	 */
	private List<String> groupIds = new ArrayList<>();
	/** User ids (as strings) of people who joined this public event from the feed. */
	private List<String> attendeeIds = new ArrayList<>();
	/**
	 * Invitees' answers: participant user id -> "accepted", "maybe" or "declined". A participant without an entry
	 * has not answered yet.
	 */
	private Map<String, String> responses = new HashMap<>();
	private List<String> tags = new ArrayList<>();
	/** The city tag the server added automatically (see CityTagService), or null. Also present in tags. */
	private String cityTag;
	private EventLocation location;
	private Boolean isPublic;
	/**
	 * The words this event can be found by (see SearchText.terms), indexed for search. Filled in automatically on
	 * every save (SearchTermsListener); never sent to or accepted from clients.
	 */
	@JsonIgnore
	private List<String> searchTerms = new ArrayList<>();

	/**
	 * Cancelled by its organiser or source (discovery: EventCancelled). It stays in the calendars of people who
	 * joined, marked as cancelled, but leaves Discover, search and profiles.
	 */
	private Boolean cancelled;

	/** Events from the discovery service: where they come from and the last message version applied (never sent to clients). */
	@JsonIgnore
	private String discoverySource;
	@JsonIgnore
	private Long discoveryVersion;
	/** The event's page at its source, e.g. its Ticketmaster page. */
	private String sourceUrl;

	// Response-only fields, filled per viewer (never stored)
	@Transient
	private String hostName;
	/** "host", "invited" or "going": how the viewer relates to the event. */
	@Transient
	private String viewerRole;
	/** The organizer's id, name and username, for invitees (never their email). */
	@Transient
	private Map<String, Object> organizer;

	public Map<String, Object> getOrganizer() {
		return organizer;
	}

	public void setOrganizer(Map<String, Object> organizer) {
		this.organizer = organizer;
	}

	/** Name and username of everyone invited, for the organizer and invitees (see EventMongoController). */
	@Transient
	private List<Map<String, Object>> participantProfiles;

	public List<Map<String, Object>> getParticipantProfiles() {
		return participantProfiles;
	}

	public void setParticipantProfiles(List<Map<String, Object>> participantProfiles) {
		this.participantProfiles = participantProfiles;
	}

	public EventDoc() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}

	public String getCreatorId() {
		return creatorId;
	}

	public void setCreatorId(String creatorId) {
		this.creatorId = creatorId;
	}

	public List<String> getParticipantIds() {
		return participantIds;
	}

	public void setParticipantIds(List<String> participantIds) {
		this.participantIds = participantIds;
	}

	public List<String> getGroupIds() {
		return groupIds;
	}

	public void setGroupIds(List<String> groupIds) {
		this.groupIds = groupIds;
	}

	public List<String> getAttendeeIds() {
		return attendeeIds;
	}

	public void setAttendeeIds(List<String> attendeeIds) {
		this.attendeeIds = attendeeIds;
	}

	public Map<String, String> getResponses() {
		return responses;
	}

	public void setResponses(Map<String, String> responses) {
		this.responses = responses;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public String getViewerRole() {
		return viewerRole;
	}

	public void setViewerRole(String viewerRole) {
		this.viewerRole = viewerRole;
	}

	public String getCityTag() {
		return cityTag;
	}

	public void setCityTag(String cityTag) {
		this.cityTag = cityTag;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public EventLocation getLocation() {
		return location;
	}

	public void setLocation(EventLocation location) {
		this.location = location;
	}

	@JsonIgnore
	public List<String> getSearchTerms() {
		return searchTerms;
	}

	@JsonIgnore
	public void setSearchTerms(List<String> searchTerms) {
		this.searchTerms = searchTerms;
	}

	public Boolean getIsPublic() {
		return isPublic;
	}

	public void setIsPublic(Boolean isPublic) {
		this.isPublic = isPublic;
	}



	public Boolean getCancelled() {
		return cancelled;
	}

	public void setCancelled(Boolean cancelled) {
		this.cancelled = cancelled;
	}

	@JsonIgnore
	public String getDiscoverySource() {
		return discoverySource;
	}

	@JsonIgnore
	public void setDiscoverySource(String discoverySource) {
		this.discoverySource = discoverySource;
	}

	@JsonIgnore
	public Long getDiscoveryVersion() {
		return discoveryVersion;
	}

	@JsonIgnore
	public void setDiscoveryVersion(Long discoveryVersion) {
		this.discoveryVersion = discoveryVersion;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public static class EventLocation {
		private String name;
		private String street;
		private String city;
		private String zipcode;
		private String country;

		public EventLocation() {
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getZipcode() {
			return zipcode;
		}

		public void setZipcode(String zipcode) {
			this.zipcode = zipcode;
		}

		public String getCountry() {
			return country;
		}

		public void setCountry(String country) {
			this.country = country;
		}
	}

}