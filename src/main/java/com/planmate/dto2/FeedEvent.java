package com.planmate.dto2;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A public event as shown on a Discover feed card. Built per viewer and deliberately small: it never carries
 * the creator's email or the ids of people who are not the viewer's friends.
 */
public class FeedEvent {

	/** Who created the event, as far as the card needs to know. */
	public static class Host {
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private final Long id;
		private final String name;
		private final String username;
		private final boolean friend;
		private final boolean you;
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private final String avatarKey;

		public Host(String name, String username, boolean friend, boolean you) {
			this(null, name, username, friend, you, null);
		}

		/** id and avatarKey are null for guests and unknown creators. */
		public Host(Long id, String name, String username, boolean friend, boolean you, String avatarKey) {
			this.id = id;
			this.name = name;
			this.username = username;
			this.friend = friend;
			this.you = you;
			this.avatarKey = avatarKey;
		}

		public Long getId() {
			return id;
		}

		public String getAvatarKey() {
			return avatarKey;
		}

		public String getName() {
			return name;
		}

		public String getUsername() {
			return username;
		}

		public boolean isFriend() {
			return friend;
		}

		public boolean isYou() {
			return you;
		}
	}

	/** One of the viewer's friends who is going. */
	public static class Friend {
		private final Long id;
		private final String firstName;
		private final String lastName;
		private final String username;
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private final String avatarKey;

		public Friend(Long id, String firstName, String lastName, String username) {
			this(id, firstName, lastName, username, null);
		}

		public Friend(Long id, String firstName, String lastName, String username, String avatarKey) {
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
			this.username = username;
			this.avatarKey = avatarKey;
		}

		public String getAvatarKey() {
			return avatarKey;
		}

		public Long getId() {
			return id;
		}

		public String getFirstName() {
			return firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public String getUsername() {
			return username;
		}
	}

	private String id;
	private String title;
	private String description;
	private Instant startTime;
	private Instant endTime;
	private EventDoc.EventLocation location;
	private List<String> tags;
	private Host host;
	private int attendeeCount;
	private List<Friend> friendsGoing;
	private boolean going;
	private boolean hosting;
	/** True when the viewer was invited (directly or through a friend group) and can answer on the card. */
	private boolean invited;
	/** The viewer's answer to the invitation: accepted, maybe, declined or pending; null when not invited. */
	private String myResponse;
	/**
	 * Search results only: why the event matched, e.g. ["title", "location", "friend"]. Possible values: title,
	 * tags, location, description, friend (a friend with that name hosts or goes), group (one of your groups with
	 * that name is invited). Null (and left out of the JSON) in the feed.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<String> matchedBy;

	/** Cancelled by its organiser or source: shown as cancelled, nobody can join it any more. */
	private boolean cancelled;

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	/** Feed only: how many more upcoming dates the same show has (they get no cards of their own). Left out when none. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Integer moreDates;

	public Integer getMoreDates() {
		return moreDates;
	}

	public void setMoreDates(Integer moreDates) {
		this.moreDates = moreDates;
	}

	/** The event's page at its source (discovery events), for "Read more here". Left out when there is none. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String sourceUrl;

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	/**
	 * Whether anyone may open the event: set on the event page (GET /events/{id}/card), so it can say who the link
	 * works for. Left out elsewhere.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean isPublic;

	public Boolean getIsPublic() {
		return isPublic;
	}

	public void setIsPublic(Boolean isPublic) {
		this.isPublic = isPublic;
	}

	public List<String> getMatchedBy() {
		return matchedBy;
	}

	public void setMatchedBy(List<String> matchedBy) {
		this.matchedBy = matchedBy;
	}

	public boolean isInvited() {
		return invited;
	}

	public void setInvited(boolean invited) {
		this.invited = invited;
	}

	public String getMyResponse() {
		return myResponse;
	}

	public void setMyResponse(String myResponse) {
		this.myResponse = myResponse;
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

	public EventDoc.EventLocation getLocation() {
		return location;
	}

	public void setLocation(EventDoc.EventLocation location) {
		this.location = location;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public Host getHost() {
		return host;
	}

	public void setHost(Host host) {
		this.host = host;
	}

	public int getAttendeeCount() {
		return attendeeCount;
	}

	public void setAttendeeCount(int attendeeCount) {
		this.attendeeCount = attendeeCount;
	}

	public List<Friend> getFriendsGoing() {
		return friendsGoing;
	}

	public void setFriendsGoing(List<Friend> friendsGoing) {
		this.friendsGoing = friendsGoing;
	}

	public boolean isGoing() {
		return going;
	}

	public void setGoing(boolean going) {
		this.going = going;
	}

	public boolean isHosting() {
		return hosting;
	}

	public void setHosting(boolean hosting) {
		this.hosting = hosting;
	}
}
