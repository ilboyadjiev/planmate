package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.PlanmateApplication;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;

/**
 * The settings against real PostgreSQL and MongoDB: privacy choices are enforced wherever the app would otherwise
 * show the data, and topics shape Discover. Alice is friends with Bob and Carol; Dave is friends with Bob (so he
 * gets Bob suggested... and Alice, through Bob). Erin knows nobody.
 */
@SpringBootTest(classes = PlanmateApplication.class)
class SettingsPrivacyIntegrationTest {

	private final String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);
	private final List<User> created = new ArrayList<>();
	private final List<EventDoc> createdEvents = new ArrayList<>();

	@Autowired
	private UserSettingsService settings;
	@Autowired
	private PeopleService people;
	@Autowired
	private FriendSuggestionService suggestions;
	@Autowired
	private FriendGroupService groups;
	@Autowired
	private FeedService feed;
	@Autowired
	private EventSearchService search;
	@Autowired
	private AccountExportService export;
	@Autowired
	private UserService users;
	@Autowired
	private FriendshipService friendships;
	@Autowired
	private EventMongoRepository events;

	private User alice, bob, carol, dave, erin;

	private User user(String username, String first) {
		User u = new User();
		u.setEmail(username + "-" + suffix + "@example.test");
		u.setUsername(username + suffix);
		u.setFirstName(first);
		u.setLastName("Zq" + suffix);
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

	private EventDoc publicEvent(String title, String creator, long startsInMinutes, List<String> tags,
			String... attendeeIds) {
		EventDoc e = new EventDoc();
		e.setTitle(title);
		e.setCreatorId(creator);
		e.setIsPublic(true);
		e.setTags(new ArrayList<>(tags));
		e.setStartTime(Instant.now().plusSeconds(startsInMinutes * 60));
		e.setEndTime(Instant.now().plusSeconds(startsInMinutes * 60 + 3600));
		e.setAttendeeIds(new ArrayList<>(Arrays.asList(attendeeIds)));
		EventDoc saved = events.save(e);
		createdEvents.add(saved);
		return saved;
	}

	private void set(User user, Object... pairs) {
		Map<String, Object> changes = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			changes.put((String) pairs[i], pairs[i + 1]);
		}
		settings.update(user.getId(), changes);
	}

	private static List<String> titles(Object cards) {
		return ((List<?>) cards).stream().map(c -> ((FeedEvent) c).getTitle()).collect(Collectors.toList());
	}

	private static List<Object> ids(List<Map<String, Object>> people) {
		return people.stream().map(p -> p.get("id")).collect(Collectors.toList());
	}

	/** Every "For you" card for the viewer, all pages. */
	private List<FeedEvent> wholeFeed(String email) {
		List<FeedEvent> all = new ArrayList<>();
		for (int page = 0; page < 50; page++) {
			FeedService.FeedPage p = feed.getFeed(email, null, page, 50);
			all.addAll(p.getEvents());
			if (!p.isHasMore()) {
				break;
			}
		}
		return all;
	}

	@BeforeEach
	void setUp() {
		alice = user("alice", "Alice");
		bob = user("bob", "Bob");
		carol = user("carol", "Carol");
		dave = user("dave", "Dave");
		erin = user("erin", "Erin");
		befriend(alice, bob);
		befriend(alice, carol);
		befriend(dave, bob);
	}

	@AfterEach
	void cleanUp() {
		events.deleteAll(createdEvents);
		for (User u : created) {
			groups.removeFromAllGroups(u.getId());
			if (users.getUserById(u.getId()) != null) {
				users.deleteUser(u);
			}
		}
	}

	@Test
	void peopleWhoTurnedOffSearchAreOnlyFoundByTheirFriends() {
		set(bob, "searchable", false);
		assertTrue(ids(people.search(alice.getEmail(), "Zq" + suffix)).contains(bob.getId()), "friends still find Bob");
		List<Object> erinFinds = ids(people.search(erin.getEmail(), "Zq" + suffix));
		assertFalse(erinFinds.contains(bob.getId()));
		assertTrue(erinFinds.contains(carol.getId()));
	}

	@Test
	void whoSeesTheEventsSomeoneGoesToOnTheirProfile() {
		publicEvent("Jazz " + suffix, carol.getEmail(), 120, Collections.emptyList(), String.valueOf(bob.getId()));

		// Default: friends only
		assertEquals(Arrays.asList("Jazz " + suffix), titles(people.profile(alice.getEmail(), bob.getId()).get("going")));
		assertTrue(titles(people.profile(erin.getEmail(), bob.getId()).get("going")).isEmpty());

		set(bob, "goingVisibility", "everyone");
		assertEquals(Arrays.asList("Jazz " + suffix), titles(people.profile(erin.getEmail(), bob.getId()).get("going")));

		set(bob, "goingVisibility", "only_me");
		assertTrue(titles(people.profile(alice.getEmail(), bob.getId()).get("going")).isEmpty());
		assertEquals(Arrays.asList("Jazz " + suffix), titles(people.profile(bob.getEmail(), bob.getId()).get("going")));
	}

	@Test
	void onlyMeAlsoHidesYouFromFeedCardsAndFromSearchingYourName() {
		EventDoc jazz = publicEvent("Jazz " + suffix, carol.getEmail(), 30, Collections.emptyList(),
				String.valueOf(bob.getId()));
		FeedEvent card = feed.toFeedEvent(jazz, alice.getEmail());
		assertEquals(1, card.getFriendsGoing().size());
		assertEquals(1, search.search(alice.getEmail(), "Bob", 0, 50).getEvents().stream()
				.filter(e -> e.getId().equals(jazz.getId())).count());

		set(bob, "goingVisibility", "only_me");
		card = feed.toFeedEvent(jazz, alice.getEmail());
		assertTrue(card.getFriendsGoing().isEmpty(), "Bob is not named");
		assertEquals(1, card.getAttendeeCount(), "but still counted");
		assertTrue(search.search(alice.getEmail(), "Bob", 0, 50).getEvents().stream()
				.noneMatch(e -> e.getId().equals(jazz.getId())), "searching Bob no longer finds where he goes");
	}

	@Test
	void peopleWhoTurnedOffSuggestionsAreNeverSuggested() {
		assertTrue(ids(suggestions.suggestionsFor(dave.getEmail())).contains(alice.getId()));
		set(alice, "showInSuggestions", false);
		assertFalse(ids(suggestions.suggestionsFor(dave.getEmail())).contains(alice.getId()));
	}

	@Test
	void peopleWhoSaidNobodyCannotBeAddedToGroups() {
		set(bob, "groupAdds", "nobody");
		ResponseStatusException refused = assertThrows(ResponseStatusException.class,
				() -> groups.create(alice.getEmail(), "Climbing " + suffix, Arrays.asList(bob.getId(), carol.getId())));
		assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
		assertTrue(refused.getReason().contains("Bob doesn't want to be added to groups"), refused.getReason());

		// Others can still be added, and Bob can still make his own groups
		groups.create(alice.getEmail(), "Climbing " + suffix, Arrays.asList(carol.getId()));
		groups.create(bob.getEmail(), "Bob's group " + suffix, Arrays.asList(alice.getId()));
	}

	@Test
	void hiddenTopicsLeaveForYouButNotTheirOwnTagAndLikedTopicsRankHigher() {
		String hidden = "#hidden" + suffix;
		String liked = "#liked" + suffix;
		publicEvent("Hidden " + suffix, carol.getEmail(), 10, Arrays.asList(hidden));
		EventDoc plain = publicEvent("Plain " + suffix, carol.getEmail(), 11, Collections.emptyList());
		EventDoc favourite = publicEvent("Liked " + suffix, carol.getEmail(), 12, Arrays.asList(liked));

		set(erin, "hiddenTags", Arrays.asList(hidden), "likedTags", Arrays.asList(liked));

		List<String> forYou = wholeFeed(erin.getEmail()).stream().map(FeedEvent::getTitle).collect(Collectors.toList());
		assertFalse(forYou.contains("Hidden " + suffix));
		assertTrue(forYou.indexOf("Liked " + suffix) < forYou.indexOf("Plain " + suffix),
				"the liked topic comes first although it starts later");
		assertEquals(Arrays.asList("Hidden " + suffix), titles(feed.getFeed(erin.getEmail(), hidden, 0, 10).getEvents()),
				"choosing the tag itself still shows it");

		FeedService.Viewer viewer = feed.viewer(erin.getEmail());
		Instant now = Instant.now();
		assertTrue(FeedService.score(favourite, viewer, now) > FeedService.score(plain, viewer, now));
	}

	@Test
	void homeCityDrivesAroundMe() {
		publicEvent("Market " + suffix, carol.getEmail(), 60, Arrays.asList("#zqcity" + suffix));
		set(erin, "homeCity", "Zqcity" + suffix);
		assertEquals("Zqcity" + suffix, users.getUserById(erin.getId()).getContactData().getCity());
		FeedService.FeedPage nearby = feed.getNearbyFeed(erin.getEmail(), 0, 50);
		assertEquals("Zqcity" + suffix, nearby.getCity());
		assertTrue(titles(nearby.getEvents()).contains("Market " + suffix));
	}

	@Test
	void exportHoldsYourDataAndOthersOnlyByName() {
		set(alice, "timeFormat", "12h");
		publicEvent("Alice's party " + suffix, alice.getEmail(), 60, Collections.emptyList());
		groups.create(alice.getEmail(), "Board games " + suffix, Arrays.asList(bob.getId()));

		Map<String, Object> data = export.export(alice.getEmail());
		@SuppressWarnings("unchecked")
		Map<String, Object> account = (Map<String, Object>) data.get("account");
		assertEquals(alice.getEmail(), account.get("email"));
		assertFalse(account.containsKey("password"));
		assertEquals("12h", ((Map<?, ?>) data.get("settings")).get("timeFormat"));
		assertEquals(2, ((List<?>) data.get("friends")).size());
		assertEquals(1, ((List<?>) data.get("groups")).size());
		List<?> createdEvents = (List<?>) ((Map<?, ?>) data.get("events")).get("created");
		assertEquals("Alice's party " + suffix, ((Map<?, ?>) createdEvents.get(0)).get("title"));
		assertNull(data.get("profilePhoto"));

		String json = data.toString();
		assertFalse(json.contains(bob.getEmail()), "friends appear without their email");
		assertFalse(json.contains("not-a-real-hash"), "never the password hash");
	}

	@Test
	void deletingTheAccountDeletesTheSettings() {
		set(erin, "searchable", false);
		assertFalse(settings.of(erin.getId()).isSearchable(), "stored");
		users.deleteUser(erin);
		assertTrue(settings.of(Arrays.asList(erin.getId())).get(erin.getId()).isSearchable(),
				"no stored settings are left, so only defaults remain");
	}
}
