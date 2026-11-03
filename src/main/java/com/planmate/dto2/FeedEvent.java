package com.planmate.dto2;

import java.time.Instant;
import java.util.List;

/**
 * A public event as shown on a Discover feed card. Built per viewer and deliberately small: it never carries
 * the creator's email or the ids of people who are not the viewer's friends.
 */
public class FeedEvent {

	/** Who created the event, as far as the card needs to know. */
	public static class Host {
		private final String name;
		private final String username;
		private final boolean friend;
		private final boolean you;

		public Host(String name, String username, boolean friend, boolean you) {
			this.name = name;
			this.username = username;
			this.friend = friend;
			this.you = you;
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

		public Friend(Long id, String firstName, String lastName, String username) {
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
			this.username = username;
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
