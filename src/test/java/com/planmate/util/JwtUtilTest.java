package com.planmate.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilTest {

	private JwtUtil jwtUtil;
	private final UserDetails alice = new User("alice@example.com", "x", Collections.emptyList());

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-that-is-long-enough-0123456789");
		jwtUtil.verifySecret();
	}

	@Test
	void accessAndRefreshTokensAreNotInterchangeable() {
		String access = jwtUtil.generateAccessToken(alice);
		String refresh = jwtUtil.generateRefreshToken(alice);

		assertTrue(jwtUtil.isAccessToken(access));
		assertFalse(jwtUtil.isRefreshToken(access));

		assertTrue(jwtUtil.isRefreshToken(refresh));
		assertFalse(jwtUtil.isAccessToken(refresh));
	}

	@Test
	void refusesMissingSecret() {
		JwtUtil missing = new JwtUtil();
		assertThrows(IllegalStateException.class, missing::verifySecret);

		JwtUtil blank = new JwtUtil();
		ReflectionTestUtils.setField(blank, "secret", "  ");
		assertThrows(IllegalStateException.class, blank::verifySecret);
	}
}
