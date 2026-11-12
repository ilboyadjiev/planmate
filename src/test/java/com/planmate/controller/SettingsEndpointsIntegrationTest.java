package com.planmate.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.planmate.PlanmateApplication;
import com.planmate.dto.User;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;

/** Settings, "Download my data" and changing the password, through HTTP against the real databases. */
@SpringBootTest(classes = PlanmateApplication.class)
@AutoConfigureMockMvc
class SettingsEndpointsIntegrationTest {

	private static final String PASSWORD = "first-password-1";
	private final String tag = Long.toString(System.nanoTime(), 36);

	@Autowired
	private MockMvc mvc;
	@Autowired
	private UserService users;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private UserDetailsService userDetails;
	@Autowired
	private JwtUtil jwt;

	private User user;
	private String token;

	private User create(String name, boolean google) {
		User u = new User();
		u.setEmail(name + "-" + tag + "@example.com");
		u.setUsername(name + "_" + tag);
		u.setFirstName("Settings");
		u.setPassword(passwordEncoder.encode(PASSWORD));
		u.setRole("ROLE_USER");
		u.setGoogleAccount(google ? true : null);
		return users.createUser(u);
	}

	private String tokenFor(User u) {
		return "Bearer " + jwt.generateAccessToken(userDetails.loadUserByUsername(u.getEmail()));
	}

	@BeforeEach
	void setUp() {
		user = create("settings", false);
		token = tokenFor(user);
	}

	@AfterEach
	void tearDown() {
		if (users.getUserById(user.getId()) != null) {
			users.deleteUser(user);
		}
	}

	@Test
	void settingsNeedALogin() throws Exception {
		mvc.perform(get("/api/v1/settings")).andExpect(result -> assertTrue(
				result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
	}

	@Test
	void changeSomeSettingsAndReadThemBack() throws Exception {
		mvc.perform(patch("/api/v1/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"timeFormat\":\"12h\",\"likedTags\":[\"Music\"],\"homeCity\":\"Munich\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeFormat").value("12h"))
				.andExpect(jsonPath("$.likedTags[0]").value("#music"))
				.andExpect(jsonPath("$.weekStart").value("monday"));

		mvc.perform(get("/api/v1/settings").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeFormat").value("12h"))
				.andExpect(jsonPath("$.homeCity").value("Munich"))
				.andExpect(jsonPath("$.passwordLogin").value(true));
	}

	@Test
	void invalidSettingsAreRefusedWithAReason() throws Exception {
		mvc.perform(patch("/api/v1/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"weekStart\":\"friday\"}"))
				.andExpect(status().isBadRequest())
				// The reason is written for people, so the app can show it as it is
				.andExpect(jsonPath("$.error").value("weekStart must be one of monday, sunday."))
				.andExpect(jsonPath("$.message").value("weekStart must be one of monday, sunday."));
	}

	@Test
	void withTheAiGamePlanTurnedOffNothingIsSentToGemini() throws Exception {
		mvc.perform(patch("/api/v1/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"aiGamePlan\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiGamePlan").value(false));

		// Refused before the game plan is even looked at, so no events leave PlanMate
		mvc.perform(get("/api/v2/ai/summary").header("Authorization", token).param("force", "true"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("The AI Game plan is turned off in your settings."));
	}

	@Autowired
	private com.planmate.dao.EventMongoRepository events;

	private com.planmate.dto2.EventDoc event(String title, String creator, java.util.Map<String, String> responses) {
		com.planmate.dto2.EventDoc e = new com.planmate.dto2.EventDoc();
		e.setTitle(title + " " + tag);
		e.setCreatorId(creator);
		e.setIsPublic(false);
		e.setStartTime(java.time.Instant.now().plusSeconds(86400));
		e.setEndTime(java.time.Instant.now().plusSeconds(90000));
		e.setParticipantIds(new java.util.ArrayList<>(responses.keySet()));
		e.setResponses(new java.util.HashMap<>(responses));
		return events.save(e);
	}

	private String createFeedKey(String query) throws Exception {
		String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/settings/calendar-feed" + query).header("Authorization", token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("calendarFeedKey").asText();
	}

	@Test
	void theCalendarSubscriptionWorksWithoutALoginAndCanBeResetOrTurnedOff() throws Exception {
		String me = String.valueOf(user.getId());
		java.util.List<com.planmate.dto2.EventDoc> made = java.util.Arrays.asList(
				event("Hosting", user.getEmail(), java.util.Collections.emptyMap()),
				event("Going", "someone@example.com", java.util.Map.of(me, "accepted")),
				event("Maybe", "someone@example.com", java.util.Map.of(me, "maybe")),
				event("Declined", "someone@example.com", java.util.Map.of(me, "declined")),
				event("Unanswered", "someone@example.com", java.util.Map.of(me, "pending")));
		try {
			String key = createFeedKey("");
			assertEquals(key, createFeedKey(""), "asking again keeps the same link");

			// Calendar apps fetch it without logging in
			String ics = mvc.perform(get("/api/v2/calendar/" + key + ".ics"))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/calendar")))
					.andReturn().getResponse().getContentAsString().replace("\r\n ", "");
			assertTrue(ics.contains("SUMMARY:Hosting " + tag));
			assertTrue(ics.contains("SUMMARY:Going " + tag));
			assertTrue(ics.contains("SUMMARY:Maybe " + tag));
			assertTrue(!ics.contains("Declined " + tag) && !ics.contains("Unanswered " + tag), "only events you are going to");
			assertTrue(ics.contains("STATUS:TENTATIVE"));

			mvc.perform(get("/api/v1/settings").header("Authorization", token)).andExpect(jsonPath("$.calendarFeedKey").value(key));

			String newKey = createFeedKey("?reset=true");
			mvc.perform(get("/api/v2/calendar/" + key + ".ics")).andExpect(status().isNotFound());
			mvc.perform(get("/api/v2/calendar/" + newKey + ".ics")).andExpect(status().isOk());

			mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.delete("/api/v1/settings/calendar-feed").header("Authorization", token)).andExpect(status().isNoContent());
			mvc.perform(get("/api/v2/calendar/" + newKey + ".ics")).andExpect(status().isNotFound());
			mvc.perform(get("/api/v2/calendar/not-a-real-key-000000000.ics")).andExpect(status().isNotFound());
		} finally {
			events.deleteAll(made);
		}
	}

	@Test
	void oneEventAsAFileFollowsTheEventPageRules() throws Exception {
		com.planmate.dto2.EventDoc mine = event("Private dinner", user.getEmail(), java.util.Collections.emptyMap());
		try {
			mvc.perform(get("/api/v2/events/" + mine.getId() + "/calendar.ics").header("Authorization", token))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("private-dinner-")))
					.andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("BEGIN:VEVENT")));
			mvc.perform(get("/api/v2/events/" + mine.getId() + "/calendar.ics")).andExpect(status().isUnauthorized());
		} finally {
			events.delete(mine);
		}
	}

	@Test
	void downloadMyDataIsAJsonFile() throws Exception {
		mvc.perform(get("/api/v1/account/export").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
				.andExpect(jsonPath("$.account.email").value(user.getEmail()))
				.andExpect(jsonPath("$.account.signIn").value("password"))
				.andExpect(jsonPath("$.settings.timeFormat").value("24h"));
	}

	private String passwordBody(String current, String next) {
		return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
	}

	@Test
	void changingThePasswordChecksTheCurrentOneAndTheLength() throws Exception {
		// 400 rather than 401: the app reads 401 as an expired login
		mvc.perform(put("/api/v1/auth/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody("wrong-password", "second-password-2")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Your current password is incorrect."));
		mvc.perform(put("/api/v1/auth/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody(PASSWORD, "short")))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/auth/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody(PASSWORD, PASSWORD)))
				.andExpect(status().isBadRequest());

		mvc.perform(put("/api/v1/auth/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody(PASSWORD, "second-password-2")))
				.andExpect(status().isOk());
		assertTrue(passwordEncoder.matches("second-password-2", users.getUserById(user.getId()).getPassword()));
	}

	@Test
	void googleAccountsCannotChangeAPasswordTheyNeverHad() throws Exception {
		User google = create("google", true);
		try {
			mvc.perform(get("/api/v1/settings").header("Authorization", tokenFor(google)))
					.andExpect(jsonPath("$.passwordLogin").value(false));
			mvc.perform(put("/api/v1/auth/password").header("Authorization", tokenFor(google))
					.contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, "second-password-2")))
					.andExpect(status().isBadRequest());
			assertEquals(true, passwordEncoder.matches(PASSWORD, users.getUserById(google.getId()).getPassword()));
		} finally {
			users.deleteUser(google);
		}
	}
}
