package com.planmate.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.PlanmateApplication;
import com.planmate.dto.User;
import com.planmate.service.AvatarService;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;

/**
 * Profile pictures end to end against the real Postgres (CI provides one): upload through the API, the picture is
 * stored as bytea and served publicly by its key with long caching, a new upload changes the key, and removing the
 * picture or the whole account deletes it. Creates its own user and deletes it afterwards.
 */
@SpringBootTest(classes = PlanmateApplication.class)
@AutoConfigureMockMvc
class AvatarIntegrationTest {

	private static final String TAG = Long.toString(System.nanoTime(), 36);

	@Autowired
	private MockMvc mvc;
	@Autowired
	private UserService userService;
	@Autowired
	private AvatarService avatarService;
	@Autowired
	private UserDetailsService userDetailsService;
	@Autowired
	private JwtUtil jwtUtil;

	private User user;
	private String token;

	@BeforeAll
	static void headless() {
		System.setProperty("java.awt.headless", "true");
	}

	@BeforeEach
	void createUser() {
		User u = new User();
		u.setEmail("avatar-test-" + TAG + "@example.com");
		u.setUsername("avatar_test_" + TAG);
		u.setFirstName("Avatar");
		u.setLastName("Test");
		u.setPassword("not-used");
		u.setRole("ROLE_USER");
		user = userService.createUser(u);
		token = "Bearer " + jwtUtil.generateAccessToken(userDetailsService.loadUserByUsername(u.getEmail()));
	}

	@AfterEach
	void deleteUser() {
		if (userService.getUserById(user.getId()) != null) {
			userService.deleteUser(user);
		}
	}

	private static byte[] picture(int width, int height, String format) throws java.io.IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setColor(java.awt.Color.ORANGE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private RequestBuilder upload(byte[] picture) {
		return multipart(HttpMethod.PUT, "/api/v1/avatars/me")
				.file(new MockMultipartFile("file", "me.png", "image/png", picture))
				.header("Authorization", token);
	}

	private String uploadAndGetKey(byte[] picture) throws Exception {
		MvcResult result = mvc.perform(upload(picture)).andExpect(status().isOk()).andReturn();
		return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("avatarKey").asText();
	}

	private String url(String key) {
		return "/api/v1/avatars/" + user.getId() + "/" + key;
	}

	@Test
	void uploadedPictureIsServedPubliclyByItsKey() throws Exception {
		String key = uploadAndGetKey(picture(800, 600, "png"));
		assertEquals(key, userService.getUserById(user.getId()).getAvatarKey());

		// No Authorization header: img tags cannot send one
		MvcResult result = mvc.perform(get(url(key)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
				.andReturn();
		byte[] jpeg = result.getResponse().getContentAsByteArray();
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
		assertNotNull(image);
		assertEquals(AvatarService.SIZE, image.getWidth());
		assertEquals(AvatarService.SIZE, image.getHeight());
	}

	@Test
	void aNewUploadReplacesThePictureAndRetiresTheOldKey() throws Exception {
		String first = uploadAndGetKey(picture(300, 300, "png"));
		String second = uploadAndGetKey(picture(300, 300, "jpeg"));

		mvc.perform(get(url(first))).andExpect(status().isNotFound());
		mvc.perform(get(url(second))).andExpect(status().isOk());
		mvc.perform(get(url("guessed-key-000000000"))).andExpect(status().isNotFound());
	}

	@Test
	void uploadingNeedsALoginAndAPicture() throws Exception {
		byte[] picture = picture(100, 100, "png");
		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/avatars/me")
				.file(new MockMultipartFile("file", "me.png", "image/png", picture)))
				.andExpect(result -> assertTrue(result.getResponse().getStatus() == 401
						|| result.getResponse().getStatus() == 403));

		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/avatars/me")
				.file(new MockMultipartFile("file", "me.svg", "image/svg+xml", "<svg/>".getBytes()))
				.header("Authorization", token))
				.andExpect(status().isBadRequest());
		assertNull(userService.getUserById(user.getId()).getAvatarKey());
	}

	@Test
	void removingThePictureOrTheAccountDeletesIt() throws Exception {
		String key = uploadAndGetKey(picture(300, 300, "png"));
		mvc.perform(delete("/api/v1/avatars/me").header("Authorization", token)).andExpect(status().isNoContent());
		mvc.perform(get(url(key))).andExpect(status().isNotFound());
		assertNull(userService.getUserById(user.getId()).getAvatarKey());

		key = uploadAndGetKey(picture(300, 300, "png"));
		userService.deleteUser(user);
		assertNull(avatarService.image(user.getId(), key));
	}
}
