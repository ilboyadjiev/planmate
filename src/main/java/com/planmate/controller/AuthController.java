package com.planmate.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.planmate.dto.User;
import com.planmate.exception.BusinessLogicException;
import com.planmate.service.UserService;
import com.planmate.util.AuthenticationRequest;
import com.planmate.util.ChangePasswordRequest;
import com.planmate.util.GoogleAuthRequest;
import com.planmate.util.JwtResponse;
import com.planmate.util.JwtUtil;
import com.planmate.util.TokenRefreshRequest;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private UserService userService;
	
	@Value("${google.client.id}")
	private String googleClientId;

	@PostMapping("/authenticate")
	public ResponseEntity<?> createAuthenticationToken(@RequestBody User user) throws Exception {
		try {
			authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Incorrect username or password");
		}

		// email is used for auth
		final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
		final String jwt = jwtUtil.generateToken(userDetails.getUsername());
		logger.info("User " + userDetails.getUsername() + " authenticated successfully, JWT generated");

		User userInfo = userService.getUserByEmail(user.getEmail());

		Map<String, String> response = new HashMap<>();
		response.put("jwt", jwt);
		response.put("username", userInfo.getUsername());
		response.put("firstName", userInfo.getFirstName());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/login")
	public ResponseEntity<?> loginUser(@RequestBody AuthenticationRequest authenticationRequest) {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authenticationRequest.getEmail(),
					authenticationRequest.getPassword()));

			final UserDetails userDetails = userDetailsService.loadUserByUsername(authenticationRequest.getEmail());

			final String accessToken = jwtUtil.generateAccessToken(userDetails);
			final String refreshToken = jwtUtil.generateRefreshToken(userDetails);

			User currentUser = userService.getUserByEmail(authenticationRequest.getEmail());

			return ResponseEntity.ok(
					new JwtResponse(accessToken, refreshToken, currentUser.getUsername(), currentUser.getFirstName()));

		} catch (BadCredentialsException e) {
			return new ResponseEntity<>("Incorrect email or password", HttpStatus.UNAUTHORIZED);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>("Server Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
		String refreshToken = request.getRefreshToken();

		try {
			String username = jwtUtil.extractUsername(refreshToken);

			UserDetails userDetails = userDetailsService.loadUserByUsername(username);
			User currentUser = userService.getUserByEmail(userDetails.getUsername());

			if (jwtUtil.validateToken(refreshToken, userDetails.getUsername())) {
				String newAccessToken = jwtUtil.generateAccessToken(userDetails);

				return ResponseEntity.ok(new JwtResponse(newAccessToken, refreshToken, currentUser.getEmail(),
						currentUser.getFirstName()));
			} else {
				return new ResponseEntity<>("Invalid Refresh Token", HttpStatus.FORBIDDEN);
			}
		} catch (Exception e) {
			return new ResponseEntity<>("Refresh Token Expired or Invalid. Please log in again.", HttpStatus.FORBIDDEN);
		}
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestBody User user) {
		if (userService.getUserByEmail(user.getEmail()) != null) {
			return ResponseEntity.badRequest().body("Email already in use");
		}

		user.setPassword(passwordEncoder.encode(user.getPassword()));
		user.setRole("ROLE_USER"); // default role
		User created = userService.createUser(user);

		final String jwt = jwtUtil.generateToken(created.getUsername());
		Map<String, String> response = new HashMap<>();
		response.put("jwt", jwt);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/check-email-exists/{emailToCheck}")
	public ResponseEntity<?> checkEmailAvailable(@PathVariable String emailToCheck) {
		logger.info("Checking available email: " + emailToCheck);
		boolean emailAvailable = Boolean.TRUE.equals(userService.checkEmailAvailable(emailToCheck));
		Map<String, Boolean> response = new HashMap<>();
		response.put("available", emailAvailable);
		logger.info("Email " + emailToCheck + " is " + (emailAvailable ? "available" : "not available"));
		return new ResponseEntity<>(response, emailAvailable ? HttpStatus.OK : HttpStatus.CONFLICT);
	}

	@GetMapping("/check-username-exists/{usernameToCheck}")
	public ResponseEntity<?> checkUsernameAvailable(@PathVariable String usernameToCheck) {
		boolean usernameAvailable = Boolean.TRUE.equals(userService.checkUsernameAvailable(usernameToCheck));
		Map<String, Boolean> response = new HashMap<>();
		response.put("available", usernameAvailable);
		return new ResponseEntity<>(response, usernameAvailable ? HttpStatus.OK : HttpStatus.CONFLICT);
	}

	@PutMapping("/password")
	@Operation(summary = "Securely change user password")
	public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request, Principal principal) {

		User currentUser = userService.getUserByEmail(principal.getName());
		if (currentUser == null) {
			throw new BusinessLogicException("User not found.", null, HttpStatus.NOT_FOUND);
		}
		if (!currentUser.getPassword().equals(request.getCurrentPassword())) {
			throw new BusinessLogicException("Current password is incorrect.", null, HttpStatus.UNAUTHORIZED);
		}

		currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userService.changePassword(currentUser, request.getNewPassword());

		return new ResponseEntity<>("Password updated successfully.", HttpStatus.OK);
	}

	@PostMapping("/google")
	public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthRequest request) {
		try {
			logger.info("Attempting Google SSO login with token");
			NetHttpTransport transport = new NetHttpTransport();
			GsonFactory jsonFactory = new GsonFactory();

			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
					.setAudience(Collections.singletonList(googleClientId)).build();

			// Verify the token received from frontend
			String token = StringUtils.trim(request.getToken());
			GoogleIdToken idToken = verifier.verify(token);

			if (idToken != null) {
				GoogleIdToken.Payload payload = idToken.getPayload();

				String email = payload.getEmail();
				String firstName = (String) payload.get("given_name");
				String lastName = (String) payload.get("family_name");
				
				User user = userService.getUserByEmail(email);

				if (user == null) {
					// User doesn't exist
					user = new User();
					user.setEmail(email);
					user.setUsername(email);
					user.setFirstName(firstName);
					user.setLastName(lastName);

					// a random, unguessable password since they use Google to log in
					user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
					user.setRole("ROLE_USER");

					user = userService.createUser(user);
					logger.info("New user registered via Google SSO: " + email);
				}

				final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
				final String accessToken = jwtUtil.generateAccessToken(userDetails);
				final String refreshToken = jwtUtil.generateRefreshToken(userDetails);

				logger.info("Google SSO login successful for email: " + email);
				return ResponseEntity
						.ok(new JwtResponse(accessToken, refreshToken, user.getUsername(), user.getFirstName()));

			} else {
				return new ResponseEntity<>("Invalid Google token.", HttpStatus.UNAUTHORIZED);
			}
		} catch (Exception e) {
			logger.error("Google Auth Error: ", e);
			return new ResponseEntity<>("Failed to process Google authentication: " + e.getMessage(),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}