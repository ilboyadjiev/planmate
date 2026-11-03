package com.planmate.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.util.ConstantsUtil;
import com.planmate.util.PublicViews;

/**
 * "People you may know": people the user is not connected with yet, found through
 * - shared friend groups (strongest: you already plan things together),
 * - mutual friends (friends of your friends).
 * Living in the same city only breaks ties between such candidates. It is never a reason on its own and never
 * shown, so suggestions do not reveal where anyone lives.
 *
 * Excluded: the user, their friends, and anyone with a pending request either way (those are shown elsewhere).
 */
@Service
public class FriendSuggestionService {

	static final int MAX_SUGGESTIONS = 10;

	private final UserService userService;
	private final FriendshipService friendshipService;
	private final FriendGroupService friendGroupService;

	public FriendSuggestionService(UserService userService, FriendshipService friendshipService,
			FriendGroupService friendGroupService) {
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.friendGroupService = friendGroupService;
	}

	private static final class Candidate {
		final User user;
		final List<User> mutualFriends = new ArrayList<>();
		final Set<String> sharedGroups = new LinkedHashSet<>();
		boolean sameCity;

		Candidate(User user) {
			this.user = user;
		}

		int score() {
			return 3 * sharedGroups.size() + 2 * mutualFriends.size() + (sameCity ? 1 : 0);
		}
	}

	public List<Map<String, Object>> suggestionsFor(String email) {
		User me = email == null ? null : userService.getUserByEmail(email);
		if (me == null) {
			return new ArrayList<>();
		}

		List<Friendship> mine = friendshipService.getFriendshipList(email);
		Set<Long> excluded = new HashSet<>();
		excluded.add(me.getId());
		List<User> friends = new ArrayList<>();
		for (Friendship f : mine) {
			User other = other(f, me.getId());
			if (other == null) {
				continue;
			}
			excluded.add(other.getId());
			if (accepted(f)) {
				friends.add(other);
			}
		}

		Map<Long, Candidate> candidates = new HashMap<>();
		for (User friend : friends) {
			for (Friendship f : friendshipService.getFriendshipList(friend.getEmail())) {
				User theirFriend = accepted(f) ? other(f, friend.getId()) : null;
				if (theirFriend != null && !excluded.contains(theirFriend.getId())) {
					candidates.computeIfAbsent(theirFriend.getId(), id -> new Candidate(theirFriend)).mutualFriends.add(friend);
				}
			}
		}
		for (FriendGroup group : friendGroupService.groupsOf(email)) {
			for (User member : group.getMembers()) {
				if (!excluded.contains(member.getId())) {
					candidates.computeIfAbsent(member.getId(), id -> new Candidate(member)).sharedGroups.add(group.getName());
				}
			}
		}

		String myCity = cityOf(me);
		candidates.values().forEach(c -> c.sameCity = myCity != null && myCity.equals(cityOf(c.user)));

		return candidates.values().stream()
				.sorted(Comparator.comparingInt(Candidate::score).reversed()
						.thenComparing(c -> StringUtils.defaultString(c.user.getUsername())))
				.limit(MAX_SUGGESTIONS)
				.map(FriendSuggestionService::view)
				.collect(Collectors.toList());
	}

	private static Map<String, Object> view(Candidate c) {
		Map<String, Object> view = new LinkedHashMap<>(PublicViews.person(c.user));
		view.put("mutualFriendCount", c.mutualFriends.size());
		// Mutual friends are the viewer's own friends, so naming them reveals nothing new
		view.put("mutualFriends", c.mutualFriends.stream().limit(2).map(PublicViews::person).collect(Collectors.toList()));
		view.put("sharedGroups", new ArrayList<>(c.sharedGroups));
		return view;
	}

	private static boolean accepted(Friendship f) {
		return ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(f.getStatus());
	}

	private static User other(Friendship f, Long myId) {
		User a = f.getUserA();
		User b = f.getUserB();
		if (a == null || b == null) {
			return null;
		}
		return myId.equals(a.getId()) ? b : a;
	}

	private static String cityOf(User user) {
		String city = user.getContactData() == null ? null : user.getContactData().getCity();
		return StringUtils.isBlank(city) ? null : StringUtils.normalizeSpace(city).toLowerCase();
	}
}
