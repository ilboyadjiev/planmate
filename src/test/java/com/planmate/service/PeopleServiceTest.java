package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
import org.springframework.web.server.ResponseStatusException;

import com.planmate.PlanmateApplication;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;

/**
 * Runs against real PostgreSQL and MongoDB. Alice is friends with Bob and Carol; Dave is friends with Bob and
 * Carol (2 mutual friends with Alice); "Daisy Fields" has no connection. Bob hosts a public event and joined another.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class PeopleServiceTest {

	private final String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);
	private final List<User> created = new ArrayList<>();
	private final List<EventDoc> createdEvents = new ArrayList<>();

	@Autowired
	private PeopleService people;
	@Autowired
	private UserService users;
	@Autowired
	private FriendshipService friendships;
	@Autowired
	private FriendGroupService groups;
	@Autowired
	private EventMongoRepository events;

	private User alice, bob, carol, dave, daisy;

	private User user(String username, String first, String last) {
		User u = new User();
		u.setEmail(username + "-" + suffix + "@example.test");
		u.setUsername(username + suffix);
		u.setFirstName(first);
		u.setLastName(last);
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

	private EventDoc publicEvent(String title, String creator, String... attendeeIds) {
		EventDoc e = new EventDoc();
		e.setTitle(title);
		e.setCreatorId(creator);
		e.setIsPublic(true);
		e.setStartTime(Instant.now().plusSeconds(86400));
		e.setEndTime(Instant.now().plusSeconds(90000));
		e.setAttendeeIds(new ArrayList<>(Arrays.asList(attendeeIds)));
		EventDoc saved = events.save(e);
		createdEvents.add(saved);
		return saved;
	}

	private static List<String> titles(Object cards) {
		return ((List<?>) cards).stream().map(c -> ((com.planmate.dto2.FeedEvent) c).getTitle()).collect(Collectors.toList());
	}

	@BeforeEach
	void setUp() {
		alice = user("alice", "Alice", "Meier");
		bob = user("bob", "Bob", "Brown");
		carol = user("carol", "Carol", "Jones");
		dave = user("dave", "Dave", "Fields");
		daisy = user("daisyf", "Daisy", "Fields");
		befriend(alice, bob);
		befriend(alice, carol);
		befriend(dave, bob);
		befriend(dave, carol);
		groups.create(alice.getEmail(), "Board games", Arrays.asList(bob.getId()));
		publicEvent("Bob's barbecue " + suffix, bob.getEmail());
		publicEvent("Street food " + suffix, carol.getEmail(), String.valueOf(bob.getId()));
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
	void searchFindsNamesAndUsernamesWithMutualFriendsFirstButNeverByEmailOrYourself() {
		List<Map<String, Object>> fields = people.search(alice.getEmail(), "fields");

		assertEquals(Arrays.asList("Dave", "Daisy"), fields.stream().map(r -> r.get("firstName")).collect(Collectors.toList()));
		assertEquals(2, fields.get(0).get("mutualFriendCount"));
		assertEquals(0, fields.get(1).get("mutualFriendCount"));

		assertEquals(1, people.search(alice.getEmail(), "Carol Jones").size()); // full name
		assertTrue(people.search(alice.getEmail(), "alice" + suffix).isEmpty()); // never yourself
		assertTrue(people.search(alice.getEmail(), "example.test").isEmpty()); // never by email
		assertTrue(people.search(alice.getEmail(), "d").isEmpty()); // too short
		assertFalse(fields.toString().contains("@example.test"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void friendsSeeWhatYouHostAndWhereYouAreGoing() {
		Map<String, Object> profile = people.profile(alice.getEmail(), bob.getId());

		assertEquals(true, profile.get("friend"));
		assertEquals(Arrays.asList("Board games"), profile.get("sharedGroups"));
		assertEquals(Arrays.asList("Bob's barbecue " + suffix), titles(profile.get("hosting")));
		assertEquals(Arrays.asList("Street food " + suffix), titles(profile.get("going")));
		assertFalse(profile.toString().contains("@example.test"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void othersSeeMutualFriendsAndPublicHostingButNotWhereSomeoneIsGoing() {
		Map<String, Object> profile = people.profile(dave.getEmail(), bob.getId()); // Dave and Bob are friends
		Map<String, Object> asStranger = people.profile(daisy.getEmail(), bob.getId());

		assertEquals(1, ((List<?>) profile.get("going")).size());
		assertEquals(false, asStranger.get("friend"));
		assertEquals(Arrays.asList("Bob's barbecue " + suffix), titles(asStranger.get("hosting")));
		assertTrue(((List<?>) asStranger.get("going")).isEmpty());

		Map<String, Object> aliceOnDave = people.profile(alice.getEmail(), dave.getId());
		assertEquals(Arrays.asList("Bob", "Carol"),
				((List<Map<String, Object>>) aliceOnDave.get("mutualFriends")).stream().map(m -> m.get("firstName"))
						.collect(Collectors.toList()));
	}

	@Test
	void unknownPeopleAreNotFound() {
		assertThrows(ResponseStatusException.class, () -> people.profile(alice.getEmail(), -1L));
	}
}
