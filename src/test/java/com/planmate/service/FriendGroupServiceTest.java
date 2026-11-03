package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.PlanmateApplication;
import com.planmate.dto.Friendship;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.User;

/**
 * Runs against a real PostgreSQL (CI provides one; see .github/workflows/deploy.yml).
 * Creates its own users with a unique suffix and deletes them afterwards.
 *
 * Friendships: Alice-Bob, Alice-Carol, Bob-Dave. Dave is not Alice's friend.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class FriendGroupServiceTest {

	private final String suffix = String.valueOf(System.nanoTime());
	private final List<User> created = new ArrayList<>();

	@Autowired
	private FriendGroupService groups;
	@Autowired
	private UserService users;
	@Autowired
	private FriendshipService friendships;
	@Autowired
	private com.planmate.dao.EventMongoRepository events;

	private final List<com.planmate.dto2.EventDoc> createdEvents = new ArrayList<>();

	private com.planmate.dto2.EventDoc groupEvent(String title, User creator, FriendGroup group, java.time.Instant start,
			User... participants) {
		com.planmate.dto2.EventDoc e = new com.planmate.dto2.EventDoc();
		e.setTitle(title);
		e.setCreatorId(creator.getEmail());
		e.setStartTime(start);
		e.setEndTime(start.plusSeconds(7200));
		e.setGroupIds(new ArrayList<>(Arrays.asList(String.valueOf(group.getId()))));
		e.setParticipantIds(Arrays.stream(participants).map(u -> String.valueOf(u.getId())).collect(Collectors.toList()));
		com.planmate.dto2.EventDoc saved = events.save(e);
		createdEvents.add(saved);
		return saved;
	}

	private List<String> participantsOf(com.planmate.dto2.EventDoc event) {
		return events.findById(event.getId()).get().getParticipantIds();
	}

	private User alice;
	private User bob;
	private User carol;
	private User dave;

	private User user(String name) {
		User u = new User();
		u.setEmail(name + "-" + suffix + "@example.test");
		u.setUsername(name + suffix);
		u.setFirstName(name);
		u.setPassword("not-a-real-hash");
		u.setRole("ROLE_USER");
		User saved = users.createUser(u);
		created.add(saved);
		return saved;
	}

	private void befriend(User a, User b) {
		Friendship request = friendships.createFriendship(a.getEmail(), b.getId());
		friendships.acceptFriendshipRequest(request.getId(), b.getEmail());
	}

	private static Set<String> names(FriendGroup group) {
		return group.getMembers().stream().map(User::getFirstName).collect(Collectors.toSet());
	}

	private static HttpStatus statusOf(Runnable call) {
		return assertThrows(ResponseStatusException.class, call::run).getStatus();
	}

	@BeforeEach
	void setUp() {
		alice = user("alice");
		bob = user("bob");
		carol = user("carol");
		dave = user("dave");
		befriend(alice, bob);
		befriend(alice, carol);
		befriend(bob, dave);
	}

	@AfterEach
	void cleanUp() {
		events.deleteAll(createdEvents);
		for (User u : created) {
			groups.removeFromAllGroups(u.getId());
			users.deleteUser(u);
		}
	}

	@Test
	void friendsCanBeInSeveralGroupsAndEveryMemberSeesTheGroup() {
		FriendGroup boardGames = groups.create(alice.getEmail(), "  Board   games ", Arrays.asList(bob.getId(), carol.getId()));
		FriendGroup sports = groups.create(alice.getEmail(), "Sports team", Arrays.asList(bob.getId()));

		assertEquals("Board games", boardGames.getName());
		assertEquals(Set.of("alice", "bob", "carol"), names(boardGames));
		assertEquals(Arrays.asList("Board games", "Sports team"),
				groups.groupsOf(bob.getEmail()).stream().map(FriendGroup::getName).collect(Collectors.toList()));
		assertEquals(1, groups.groupsOf(carol.getEmail()).size());
		assertTrue(groups.groupsOf(dave.getEmail()).isEmpty());
		assertEquals(Set.of("alice", "bob"), names(sports));
	}

	@Test
	void anyMemberCanAddTheirOwnFriendsButNobodyElses() {
		FriendGroup group = groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId()));

		// Dave is Bob's friend, not Alice's
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> groups.addMembers(alice.getEmail(), group.getId(), Arrays.asList(dave.getId()))));
		FriendGroup withDave = groups.addMembers(bob.getEmail(), group.getId(), Arrays.asList(dave.getId()));

		assertEquals(Set.of("alice", "bob", "dave"), names(withDave));
	}

	@Test
	void thereIsNoOwnerButOutsidersCannotTouchTheGroup() {
		FriendGroup group = groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId()));

		assertEquals("Game night", groups.rename(bob.getEmail(), group.getId(), "Game night").getName());
		assertEquals(Set.of("bob"), names(groups.removeMember(bob.getEmail(), group.getId(), alice.getId())));

		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> groups.rename(carol.getEmail(), group.getId(), "Mine now")));
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> groups.removeMember(alice.getEmail(), group.getId(), bob.getId())));
		assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> groups.rename(bob.getEmail(), group.getId(), "   ")));
	}

	@Test
	void aGroupDisappearsWhenItsLastMemberLeaves() {
		FriendGroup group = groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId()));

		groups.removeMember(alice.getEmail(), group.getId(), alice.getId());
		assertNull(groups.removeMember(bob.getEmail(), group.getId(), bob.getId()));

		assertTrue(groups.groupsOf(bob.getEmail()).isEmpty());
		assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> groups.rename(bob.getEmail(), group.getId(), "Back")));
	}

	@Test
	void deletingAnAccountTakesThemOutOfTheirGroups() {
		FriendGroup group = groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId(), carol.getId()));

		groups.removeFromAllGroups(carol.getId());

		assertEquals(Set.of("alice", "bob"), names(groups.groupsOf(alice.getEmail()).get(0)));
		assertEquals(group.getId(), groups.groupsOf(bob.getEmail()).get(0).getId());
	}

	@Test
	void newMembersAreInvitedToUpcomingGroupEventsOnly() {
		FriendGroup group = groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId()));
		java.time.Instant now = java.time.Instant.now();
		com.planmate.dto2.EventDoc nextWeek = groupEvent("Dixit", alice, group, now.plusSeconds(7 * 86400), bob);
		com.planmate.dto2.EventDoc lastWeek = groupEvent("Catan", alice, group, now.minusSeconds(7 * 86400), bob);
		com.planmate.dto2.EventDoc ongoing = groupEvent("Chess", alice, group, now.minusSeconds(600), bob);
		com.planmate.dto2.EventDoc otherGroup = groupEvent("Football", alice, groups.create(alice.getEmail(), "Sports", null),
				now.plusSeconds(86400));
		com.planmate.dto2.EventDoc davesOwn = groupEvent("Dave's game", dave, group, now.plusSeconds(86400));

		// Dave is Bob's friend, so Bob can add him
		groups.addMembers(bob.getEmail(), group.getId(), Arrays.asList(dave.getId()));

		String daveId = String.valueOf(dave.getId());
		assertTrue(participantsOf(nextWeek).contains(daveId));
		assertEquals(Arrays.asList(String.valueOf(bob.getId())), participantsOf(lastWeek));
		assertEquals(Arrays.asList(String.valueOf(bob.getId())), participantsOf(ongoing));
		assertTrue(participantsOf(otherGroup).isEmpty());
		assertTrue(participantsOf(davesOwn).isEmpty()); // never invited to his own event

		// Adding him again does not invite him twice
		groups.addMembers(alice.getEmail(), group.getId(), Arrays.asList(bob.getId()));
		assertEquals(2, participantsOf(nextWeek).size());
	}
}
