package com.planmate.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.util.AuthenticationRequest;

class AuthControllerTest {

	private AuthenticationManager authenticationManager;
	private AuthController controller;

	@BeforeEach
	void setUp() {
		authenticationManager = mock(AuthenticationManager.class);
		controller = new AuthController();
		ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
	}

	private HttpStatus login() {
		ResponseEntity<?> response = controller.loginUser(new AuthenticationRequest(null, "a@example.com", "secret"));
		return HttpStatus.valueOf(response.getStatusCodeValue());
	}

	// A wrong password and an unknown email must look identical, or the response reveals which emails are registered.

	@Test
	void wrongPasswordIsRejectedAsUnauthorized() {
		when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
		assertEquals(HttpStatus.UNAUTHORIZED, login());
	}

	@Test
	void unknownEmailLooksTheSameAsAWrongPassword() {
		when(authenticationManager.authenticate(any())).thenThrow(new UsernameNotFoundException("no such user"));
		assertEquals(HttpStatus.UNAUTHORIZED, login()); // not 500, which would leak that the email is not registered
	}
}
