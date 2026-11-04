package com.planmate.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.util.ConstantsUtil;
import com.planmate.util.PublicViews;

/**
 * Finding people and their profile pages. Everything here shows names and usernames only, never emails or
 * contact details.
 *
 * A profile shows mutual friends, shared groups and the person's upcoming public events. Public events they joined
 * are shown to their friends (and themselves) unless they chose otherwise in their settings (going visibility).
 */
@Service
public class PeopleService {

	static final int MIN_SEARCH_LENGTH = 2;
	static final int MAX_SEARCH_RESULTS = 20;
	static final int MAX_PROFILE_EVENTS = 10;

	private final UserService userService;
	private final FriendshipService friendshipService;
	private final FriendGroupService friendGroupService;
	private final EventMongoRepository events;
	private final FeedService feedService;
	private final AvailabilityService availability;
	private final UserSettingsService settings;

	public PeopleService(UserService userService, FriendshipService friendshipService,
			FriendGroupService friendGroupService, EventMongoRepository events, FeedService feedService,
			AvailabilityService availability, UserSettingsService settings) {
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.friendGroupService = friendGroupService;
		this.events = events;
		this.feedService = feedService;
		this.availability = availability;
		this.settings = settings;
	}

	/**
	 * Times when the viewer and a friend are both free (see AvailabilityService). Friends only: nobody else may
	 * learn when someone is free. 404 for anyone who is not the viewer's friend.
	 */
	public Map<String, Object> sharedFreeSlots(String viewerEmail, Long friendId, java.time.LocalDate fromDay,
			int days, int minutes, java.time.ZoneId zone) {
		User[] pair = meAndFriend(viewerEmail, friendId);
		java.time.LocalDate start = fromDay != null ? fromDay : java.time.LocalDate.now(zone);
		return availability.sharedFreeSlots(pair[0], pair[1], start, days, minutes, zone, Instant.now());
	}

	/** Whether the viewer and a friend are free at a given time ({ "youFree": .., "theyFree": .. }). Friends only. */
	public Map<String, Object> availabilityAt(String viewerEmail, Long friendId, Instant start, Instant end) {
		User[] pair = meAndFriend(viewerEmail, friendId);
		if (start == null || end == null || !end.isAfter(start)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end must be after the start.");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("youFree", availability.isFree(pair[0], start, end));
		result.put("theyFree", availability.isFree(pair[1], start, end));
		return result;
	}

	private User[] meAndFriend(String viewerEmail, Long friendId) {
		User me = viewerEmail == null ? null : userService.getUserByEmail(viewerEmail);
		if (me == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		User friend = friendId == null ? null : friendsOf(viewerEmail).get(friendId);
		if (friend == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You can only plan with your friends.");
		}
		return new User[] { me, friend };
	}

	/**
	 * People matching the text by username or name; those you have mutual friends with come first. People who
	 * turned off "Find me by name" only show up for their friends.
	 */
	public List<Map<String, Object>> search(String viewerEmail, String term) {
		String text = StringUtils.normalizeSpace(term);
		if (viewerEmail == null || text == null || text.length() < MIN_SEARCH_LENGTH) {
			return new ArrayList<>();
		}
		Map<Long, User> myFriends = friendsOf(viewerEmail);
		List<User> found = userService.searchUsernames(viewerEmail, text);
		Map<Long, com.planmate.dto.UserSettings> theirSettings = settings
				.of(found.stream().map(User::getId).collect(Collectors.toList()));
		List<Map<String, Object>> results = new ArrayList<>();
		for (User person : found) {
			if (!myFriends.containsKey(person.getId()) && !theirSettings.get(person.getId()).isSearchable()) {
				continue;
			}
			List<User> mutual = mutualFriends(myFriends, person);
			Map<String, Object> view = new LinkedHashMap<>(PublicViews.person(person));
			view.put("mutualFriendCount", mutual.size());
			view.put("mutualFriends", mutual.stream().limit(2).map(PublicViews::person).collect(Collectors.toList()));
			results.add(view);
		}
		results.sort(Comparator.comparingInt((Map<String, Object> v) -> -((Integer) v.get("mutualFriendCount")))
				.thenComparing(v -> StringUtils.defaultString((String) v.get("username"))));
		return results.stream().limit(MAX_SEARCH_RESULTS).collect(Collectors.toList());
	}

	/** Someone's profile as the viewer may see it. 404 for unknown people. */
	public Map<String, Object> profile(String viewerEmail, Long personId) {
		User me = viewerEmail == null ? null : userService.getUserByEmail(viewerEmail);
		if (me == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		User person = personId == null ? null : userService.getUserById(personId);
		if (person == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found.");
		}
		boolean self = me.getId().equals(person.getId());
		Map<Long, User> myFriends = friendsOf(viewerEmail);
		boolean friend = myFriends.containsKey(person.getId());

		Map<String, Object> profile = new LinkedHashMap<>(PublicViews.person(person));
		profile.put("self", self);
		profile.put("friend", friend);
		List<User> mutual = self ? new ArrayList<>() : mutualFriends(myFriends, person);
		profile.put("mutualFriends", mutual.stream().map(PublicViews::person).collect(Collectors.toList()));
		profile.put("sharedGroups", self ? new ArrayList<>()
				: friendGroupService.groupsOf(viewerEmail).stream()
						.filter(g -> g.hasMember(person.getId()))
						.map(FriendGroup::getName)
						.collect(Collectors.toList()));

		Instant now = Instant.now();
		PageRequest soonest = PageRequest.of(0, MAX_PROFILE_EVENTS, Sort.by("startTime").ascending());
		profile.put("hosting", events.findUpcomingPublicHostedBy(person.getEmail(), now, soonest).stream()
				.map(e -> feedService.toFeedEvent(e, viewerEmail)).collect(Collectors.toList()));
		// Which public events someone joined: their friends by default, or as they chose in their settings
		boolean showGoing = UserSettingsService.showsGoingTo(settings.of(person.getId()), self, friend);
		profile.put("going", showGoing
				? events.findUpcomingPublicAttendedBy(String.valueOf(person.getId()), now, soonest).stream()
						.map(e -> feedService.toFeedEvent(e, viewerEmail)).collect(Collectors.toList())
				: new ArrayList<>());
		return profile;
	}

	private Map<Long, User> friendsOf(String email) {
		User user = userService.getUserByEmail(email);
		if (user == null) {
			return new LinkedHashMap<>();
		}
		Map<Long, User> friends = new LinkedHashMap<>();
		for (Friendship f : friendshipService.getFriendshipList(email)) {
			if (!ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(f.getStatus())
					|| f.getUserA() == null || f.getUserB() == null) {
				continue;
			}
			User other = user.getId().equals(f.getUserA().getId()) ? f.getUserB() : f.getUserA();
			friends.put(other.getId(), other);
		}
		return friends;
	}

	private List<User> mutualFriends(Map<Long, User> myFriends, User person) {
		if (myFriends.isEmpty() || person.getEmail() == null) {
			return new ArrayList<>();
		}
		Set<Long> theirs = friendsOf(person.getEmail()).keySet();
		return theirs.stream().map(myFriends::get).filter(Objects::nonNull)
				.sorted(Comparator.comparing(u -> StringUtils.defaultString(u.getFirstName())))
				.collect(Collectors.toList());
	}
}
