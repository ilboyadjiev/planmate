package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dao.UserDAO;
import com.planmate.dao.UserSettingsDao;
import com.planmate.dto.User;
import com.planmate.dto.UserSettings;

class UserSettingsServiceTest {

	private UserSettingsDao dao;
	private UserDAO users;
	private UserSettingsService service;
	private User alice;
	private UserSettings stored;

	@BeforeEach
	void setUp() {
		dao = mock(UserSettingsDao.class);
		users = mock(UserDAO.class);
		service = new UserSettingsService(dao, users);
		alice = new User();
		alice.setId(1L);
		alice.setEmail("alice@example.com");
		alice.setUsername("alice");
		when(users.getUserById(1L)).thenReturn(alice);
		stored = null;
		when(dao.get(1L)).thenAnswer(inv -> stored);
		org.mockito.Mockito.doAnswer(inv -> {
			stored = inv.getArgument(0);
			return null;
		}).when(dao).save(any());
	}

	private static Map<String, Object> change(Object... pairs) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private HttpStatus refused(Map<String, Object> changes) {
		return assertThrows(ResponseStatusException.class, () -> service.update(1L, changes)).getStatus();
	}

	@Test
	void withoutAnyChangeEveryoneHasTodaysBehaviour() {
		Map<String, Object> view = service.view(alice);
		assertEquals("monday", view.get("weekStart"));
		assertEquals("24h", view.get("timeFormat"));
		assertEquals("auto", view.get("calendarView"));
		assertEquals("friends", view.get("goingVisibility"));
		assertEquals(true, view.get("showInSuggestions"));
		assertEquals("friends", view.get("groupAdds"));
		assertEquals(true, view.get("searchable"));
		assertEquals(true, view.get("aiGamePlan"));
		assertEquals(Collections.emptyList(), view.get("likedTags"));
		assertEquals(Collections.emptyList(), view.get("hiddenTags"));
		assertNull(view.get("homeCity"));
		assertEquals(true, view.get("passwordLogin"));
	}

	@Test
	void changesOnlyTheGivenSettings() {
		service.update(1L, change("timeFormat", "12h", "searchable", false, "aiGamePlan", false));
		Map<String, Object> view = service.update(1L, change("weekStart", "Sunday"));
		assertEquals("sunday", view.get("weekStart"));
		assertEquals("12h", view.get("timeFormat"));
		assertEquals(false, view.get("searchable"));
		assertEquals(false, view.get("aiGamePlan"));
		assertEquals("friends", view.get("goingVisibility"));
	}

	@Test
	void refusesUnknownSettingsAndValuesWithoutSavingAnything() {
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("theme", "dark")));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("weekStart", "tuesday")));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("goingVisibility", "strangers")));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("calendarView", 3)));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("searchable", "no")));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("likedTags", "#music")));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("likedTags", Arrays.asList("<script>"))));
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("homeCity", "x".repeat(81))));
		verify(dao, never()).save(any());
	}

	@Test
	void topicsAreNormalisedLikeEventTagsAndDeduplicated() {
		Map<String, Object> view = service.update(1L,
				change("likedTags", Arrays.asList("Music", "#music", " #Food ", "street food", "")));
		assertEquals(Arrays.asList("#music", "#food", "#streetfood"), view.get("likedTags"));
	}

	@Test
	void aTopicIsEitherLikedOrHiddenTheLatestChoiceWins() {
		service.update(1L, change("likedTags", Arrays.asList("#music", "#gaming")));
		Map<String, Object> view = service.update(1L, change("hiddenTags", Arrays.asList("#gaming")));
		assertEquals(Arrays.asList("#music"), view.get("likedTags"));
		assertEquals(Arrays.asList("#gaming"), view.get("hiddenTags"));

		view = service.update(1L, change("likedTags", Arrays.asList("#music", "#gaming")));
		assertEquals(Arrays.asList("#music", "#gaming"), view.get("likedTags"));
		assertEquals(Collections.emptyList(), view.get("hiddenTags"));
	}

	@Test
	void atMostTwentyTopics() {
		List<String> many = new java.util.ArrayList<>();
		for (int i = 0; i < 21; i++) {
			many.add("#topic" + i);
		}
		assertEquals(HttpStatus.BAD_REQUEST, refused(change("likedTags", many)));
	}

	@Test
	void homeCityIsStoredInTheContactDataWhereTheFeedReadsIt() {
		Map<String, Object> view = service.update(1L, change("homeCity", "  München  "));
		assertEquals("München", alice.getContactData().getCity());
		assertEquals("München", view.get("homeCity"));
		verify(users).updateUser(alice);

		service.update(1L, change("homeCity", ""));
		assertNull(alice.getContactData().getCity());
	}

	@Test
	void googleAccountsHaveNoPasswordToChange() {
		User google = new User();
		google.setEmail("g@example.com");
		google.setUsername("someone");
		google.setGoogleAccount(true);
		assertFalse(UserSettingsService.hasPasswordLogin(google));

		// Made before accounts were marked: Google sign-ups got their email as username
		User oldGoogle = new User();
		oldGoogle.setEmail("old@example.com");
		oldGoogle.setUsername("old@example.com");
		assertFalse(UserSettingsService.hasPasswordLogin(oldGoogle));

		assertTrue(UserSettingsService.hasPasswordLogin(alice));
	}

	@Test
	void whoSeesTheEventsSomeoneGoesTo() {
		UserSettings friends = new UserSettings(2L);
		assertTrue(UserSettingsService.showsGoingTo(friends, false, true));
		assertFalse(UserSettingsService.showsGoingTo(friends, false, false));
		assertTrue(UserSettingsService.showsGoingTo(friends, true, false));

		UserSettings everyone = new UserSettings(2L);
		everyone.setGoingVisibility("everyone");
		assertTrue(UserSettingsService.showsGoingTo(everyone, false, false));

		UserSettings onlyMe = new UserSettings(2L);
		onlyMe.setGoingVisibility("only_me");
		assertFalse(UserSettingsService.showsGoingTo(onlyMe, false, true));
		assertTrue(UserSettingsService.showsGoingTo(onlyMe, true, false));
	}

	@Test
	void settingsOfSeveralPeopleAlwaysHaveAnEntryForEachOne() {
		UserSettings bobs = new UserSettings(2L);
		bobs.setSearchable(false);
		when(dao.getAll(any())).thenReturn(Collections.singletonList(bobs));
		Map<Long, UserSettings> all = service.of(Arrays.asList(2L, 3L));
		assertFalse(all.get(2L).isSearchable());
		assertTrue(all.get(3L).isSearchable());
	}
}
