package com.planmate.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;

/**
 * What other people may see about a user. Never return User entities about someone else: they carry the email,
 * role and contact details (address, mobile), which only the account owner may see (via /users/profile).
 *
 * Builds plain maps rather than changing the entities, so nothing here can be persisted by accident.
 */
public final class PublicViews {

	private PublicViews() {
	}

	/** Name and username: what anyone logged in may see, e.g. in search results. */
	public static Map<String, Object> person(User user) {
		if (user == null) {
			return null;
		}
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("id", user.getId());
		view.put("username", user.getUsername());
		view.put("firstName", user.getFirstName());
		view.put("lastName", user.getLastName());
		return view;
	}

	public static List<Map<String, Object>> people(List<User> users) {
		return users.stream().map(PublicViews::person).collect(Collectors.toList());
	}

	/**
	 * A friendship as seen by one of its two people. Accepted friends also see each other's email (the calendar
	 * uses it to name event organizers); pending requests never reveal it.
	 */
	public static Map<String, Object> friendship(Friendship friendship) {
		boolean accepted = ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(friendship.getStatus());
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("id", friendship.getId());
		view.put("status", friendship.getStatus());
		view.put("userA", friendView(friendship.getUserA(), accepted));
		view.put("userB", friendView(friendship.getUserB(), accepted));
		return view;
	}

	public static List<Map<String, Object>> friendships(List<Friendship> friendships) {
		return friendships.stream().map(PublicViews::friendship).collect(Collectors.toList());
	}

	/** A friend group: its members are shown by name and username only, as they may not all be friends. */
	public static Map<String, Object> group(FriendGroup group) {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("id", group.getId());
		view.put("name", group.getName());
		view.put("createdAt", group.getCreationDate());
		view.put("members", people(group.getMembers()));
		return view;
	}

	private static Map<String, Object> friendView(User user, boolean includeEmail) {
		Map<String, Object> view = person(user);
		if (view != null && includeEmail) {
			view.put("email", user.getEmail());
		}
		return view;
	}
}
