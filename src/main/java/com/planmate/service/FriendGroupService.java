package com.planmate.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dao.FriendGroupDao;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.util.ConstantsUtil;

/**
 * Friend groups. There is no owner; the rules are the same for every member:
 * - any member can rename the group, add members and remove members,
 * - you can only add your own accepted friends,
 * - anyone can leave, and a group without members is deleted,
 * - people added to a group are invited to the group's upcoming events (not to past or ongoing ones).
 * Non-members cannot see a group at all (404, so group ids cannot be probed).
 */
@Service
@Transactional
public class FriendGroupService {

	public static final int MAX_MEMBERS = 50;

	private final FriendGroupDao groupDao;
	private final UserService userService;
	private final FriendshipService friendshipService;
	private final EventMongoService eventService;

	public FriendGroupService(FriendGroupDao groupDao, UserService userService, FriendshipService friendshipService,
			EventMongoService eventService) {
		this.groupDao = groupDao;
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.eventService = eventService;
	}

	public List<FriendGroup> groupsOf(String email) {
		return groupDao.getGroupsOfMember(me(email).getId());
	}

	public FriendGroup create(String email, String name, Collection<Long> memberIds) {
		User me = me(email);
		FriendGroup group = new FriendGroup();
		group.setName(validName(name));
		group.setCreationDate(new Timestamp(System.currentTimeMillis()));
		group.getMembers().add(me);
		addFriends(group, me, memberIds);
		return groupDao.save(group);
	}

	public FriendGroup rename(String email, Long groupId, String name) {
		FriendGroup group = groupOfMember(groupId, me(email));
		group.setName(validName(name));
		return groupDao.save(group);
	}

	public FriendGroup addMembers(String email, Long groupId, Collection<Long> userIds) {
		User me = me(email);
		FriendGroup group = groupOfMember(groupId, me);
		List<User> added = addFriends(group, me, userIds);
		FriendGroup saved = groupDao.save(group);
		Instant now = Instant.now();
		for (User user : added) {
			eventService.inviteToUpcomingGroupEvents(group.getId(), String.valueOf(user.getId()), user.getEmail(), now);
		}
		return saved;
	}

	/** Removes a member, or the caller themselves (leaving). Returns null when the group was deleted. */
	public FriendGroup removeMember(String email, Long groupId, Long userId) {
		FriendGroup group = groupOfMember(groupId, me(email));
		if (!group.hasMember(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This person is not in the group.");
		}
		group.getMembers().removeIf(m -> userId.equals(m.getId()));
		if (group.getMembers().isEmpty()) {
			groupDao.delete(group);
			return null;
		}
		return groupDao.save(group);
	}

	/** Account deletion: takes the user out of every group and deletes groups left without members. */
	public void removeFromAllGroups(Long userId) {
		for (FriendGroup group : groupDao.getGroupsOfMember(userId)) {
			group.getMembers().removeIf(m -> userId.equals(m.getId()));
			if (group.getMembers().isEmpty()) {
				groupDao.delete(group);
			} else {
				groupDao.save(group);
			}
		}
	}

	/** Adds the given friends of mine to the group and returns the people who were actually new. */
	private List<User> addFriends(FriendGroup group, User me, Collection<Long> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return new ArrayList<>();
		}
		Map<Long, User> friends = acceptedFriends(me);
		List<Long> wanted = userIds.stream().filter(Objects::nonNull).distinct()
				.filter(id -> !group.hasMember(id)).collect(Collectors.toList());
		if (!friends.keySet().containsAll(wanted)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can only add your friends.");
		}
		if (group.getMembers().size() + wanted.size() > MAX_MEMBERS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"A group can have at most " + MAX_MEMBERS + " members.");
		}
		List<User> added = wanted.stream().map(friends::get).collect(Collectors.toList());
		group.getMembers().addAll(added);
		return added;
	}

	private Map<Long, User> acceptedFriends(User me) {
		return friendshipService.getFriendshipList(me.getEmail()).stream()
				.filter(f -> ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(f.getStatus()))
				.map(f -> other(f, me))
				.filter(u -> u != null && u.getId() != null)
				.collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
	}

	private static User other(Friendship friendship, User me) {
		User a = friendship.getUserA();
		return a != null && me.getId().equals(a.getId()) ? friendship.getUserB() : a;
	}

	private FriendGroup groupOfMember(Long groupId, User me) {
		FriendGroup group = groupId == null ? null : groupDao.getById(groupId);
		if (group == null || !group.hasMember(me.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found.");
		}
		return group;
	}

	private User me(String email) {
		User me = email == null ? null : userService.getUserByEmail(email);
		if (me == null || me.getId() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return me;
	}

	private static String validName(String name) {
		String trimmed = StringUtils.normalizeSpace(name);
		if (StringUtils.isEmpty(trimmed)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the group a name.");
		}
		if (trimmed.length() > FriendGroup.MAX_NAME_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Group names can be at most " + FriendGroup.MAX_NAME_LENGTH + " characters.");
		}
		return trimmed;
	}
}
