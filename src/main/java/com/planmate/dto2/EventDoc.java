package com.planmate.dto2;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
	/** User ids (as strings) of people who joined this public event from the feed. */
	private List<String> attendeeIds = new ArrayList<>();
	private List<String> tags = new ArrayList<>();
	private EventLocation location;
	private Boolean isPublic;

	// Response-only fields, filled per viewer (never stored)
	@Transient
	private String hostName;
	/** "host", "invited" or "going": how the viewer relates to the event. */
	@Transient
	private String viewerRole;

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

	public List<String> getAttendeeIds() {
		return attendeeIds;
	}

	public void setAttendeeIds(List<String> attendeeIds) {
		this.attendeeIds = attendeeIds;
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

	public Boolean getIsPublic() {
		return isPublic;
	}

	public void setIsPublic(Boolean isPublic) {
		this.isPublic = isPublic;
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

	public String toSimplifiedString() {
		return "Event title=" + title + ", startTime=" + startTime + ", endTime=" + endTime + ", description="
				+ description + ", location=" + location + "]";
	}
}