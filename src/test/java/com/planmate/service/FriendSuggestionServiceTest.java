package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.planmate.PlanmateApplication;
import com.planmate.dto.Contact;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;

/**
 * Runs against a real PostgreSQL. Alice (Munich) is friends with Bob and Carol.
 * - Dave: friend of Bob and Carol (2 mutual friends)
 * - Erin: friend of Bob (1 mutual), lives in Munich
 * - Frank: in Alice's group "Board games", no mutual friends
 * - Gina: Alice already sent her a request (not suggested)
 * - Hank: lives in Munich, no other connection (never suggested: the city alone is no reason)
 */
@SpringBootTest(classes = PlanmateApplication.class)
class FriendSuggestionServiceTest {

	private final String suffix = String.valueOf(System.nanoTime());
	private final List<User> created = new ArrayList<>();

	@Autowired
	private FriendSuggestionService suggestions;
	@Autowired
	private FriendshipService friendships;
	@Autowired
	private FriendGroupService groups;
	@Autowired
	private UserService users;

	private User alice, bob, carol, dave, erin, frank, gina, hank;

	private User user(String name, String city) {
		User u = new User();
		u.setEmail(name + "-" + suffix + "@example.test");
		u.setUsername(name + suffix);
		u.setFirstName(name);
		u.setPassword("not-a-real-hash");
		u.setRole("ROLE_USER");
		if (city != null) {
			Contact contact = new Contact();
			contact.setCity(city);
			u.setContactData(contact);
		}
		User saved = users.createUser(u);
		created.add(saved);
		return saved;
	}

	private Friendship befriend(User a, User b) {
		Friendship request = friendships.createFriendship(a.getEmail(), b.getId());
		return friendships.acceptFriendshipRequest(request.getId(), b.getEmail());
	}

	private List<String> suggestedNames() {
		return suggestions.suggestionsFor(alice.getEmail()).stream().map(s -> (String) s.get("firstName"))
				.collect(Collectors.toList());
	}

	@BeforeEach
	void setUp() {
		alice = user("alice", "Munich");
		bob = user("bob", null);
		carol = user("carol", null);
		dave = user("dave", null);
		erin = user("erin", "munich");
		frank = user("frank", null);
		gina = user("gina", null);
		hank = user("hank", "Munich");
		befriend(alice, bob);
		befriend(alice, carol);
		befriend(bob, dave);
		befriend(carol, dave);
		befriend(bob, erin);
		befriend(alice, frank); // to put Frank in the group, then unfriend below
		groups.create(alice.getEmail(), "Board games", Arrays.asList(frank.getId(), bob.getId()));
		Friendship af = friendships.getFriendshipList(alice.getEmail()).stream()
				.filter(f -> f.getUserB().getId().equals(frank.getId())).findFirst().get();
		friendships.removeFriendship(af.getId(), alice.getEmail());
		friendships.createFriendship(alice.getEmail(), gina.getId());
	}

	@AfterEach
	void cleanUp() {
		for (User u : created) {
			groups.removeFromAllGroups(u.getId());
			users.deleteUser(u);
		}
	}

	@Test
	void suggestsGroupMatesAndFriendsOfFriendsButNotConnectionsOrMereNeighbours() {
		// Scores: Dave 2 mutual friends = 4; Frank shared group = 3; Erin 1 mutual friend + same city = 3
		List<String> names = suggestedNames();

		assertEquals("dave", names.get(0));
		assertTrue(names.containsAll(Arrays.asList("erin", "frank")));
		assertFalse(names.contains("bob")); // already a friend
		assertFalse(names.contains("gina")); // request pending
		assertFalse(names.contains("hank")); // only the same city
		assertFalse(names.contains("alice"));
	}

	@Test
	void suggestionsSayWhyButNeverWhereSomeoneLives() {
		Map<String, Object> dave = suggestions.suggestionsFor(alice.getEmail()).get(0);
		Map<String, Object> frank = suggestions.suggestionsFor(alice.getEmail()).stream()
				.filter(s -> "frank".equals(s.get("firstName"))).findFirst().get();

		assertEquals(2, dave.get("mutualFriendCount"));
		assertEquals(Arrays.asList("Board games"), frank.get("sharedGroups"));
		String all = suggestions.suggestionsFor(alice.getEmail()).toString();
		assertFalse(all.toLowerCase().contains("munich"));
		assertFalse(all.contains("@example.test"));
	}

	@Test
	void requestsCanBeDeclinedCancelledAndFriendsRemovedOnlyByThePeopleInvolved() {
		Friendship pending = friendships.createFriendship(dave.getEmail(), alice.getId());
		assertFalse(friendships.removeFriendship(pending.getId(), erin.getEmail())); // not involved
		assertTrue(friendships.removeFriendship(pending.getId(), alice.getEmail())); // declined

		Friendship sent = friendships.createFriendship(alice.getEmail(), hank.getId());
		assertTrue(friendships.removeFriendship(sent.getId(), alice.getEmail())); // cancelled

		Friendship withBob = friendships.getFriendshipList(alice.getEmail()).stream()
				.filter(f -> f.getUserB().getId().equals(bob.getId())).findFirst().get();
		assertTrue(friendships.removeFriendship(withBob.getId(), bob.getEmail())); // unfriended
		assertFalse(friendships.removeFriendship(withBob.getId(), bob.getEmail())); // already gone

		// After declining, Dave can ask again
		friendships.createFriendship(dave.getEmail(), alice.getId());
	}
}
