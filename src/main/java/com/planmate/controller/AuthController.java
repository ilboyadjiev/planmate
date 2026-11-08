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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
import com.planmate.service.UserSettingsService;
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
	static final int MIN_PASSWORD_LENGTH = 8;
	/** BCrypt only uses the first 72 bytes */
	static final int MAX_PASSWORD_LENGTH = 72;

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
					new JwtResponse(accessToken, refreshToken, currentUser.getUsername(), currentUser.getFirstName(), currentUser.getEmail()));

		} catch (AuthenticationException e) {
			// Every authentication failure (wrong password, unknown user, disabled, etc.) looks the same to the
			// client, so the response never reveals whether an email is registered.
			return new ResponseEntity<>("Incorrect email or password", HttpStatus.UNAUTHORIZED);
		} catch (Exception e) {
			logger.error("Login failed", e);
			return new ResponseEntity<>("Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
		String refreshToken = request.getRefreshToken();

		try {
			String username = jwtUtil.extractUsername(refreshToken);
			if (!jwtUtil.isRefreshToken(refreshToken)) {
				return new ResponseEntity<>("Invalid Refresh Token", HttpStatus.FORBIDDEN);
			}

			UserDetails userDetails = userDetailsService.loadUserByUsername(username);
			User currentUser = userService.getUserByEmail(userDetails.getUsername());

			if (jwtUtil.validateToken(refreshToken, userDetails.getUsername())) {
				String newAccessToken = jwtUtil.generateAccessToken(userDetails);

				return ResponseEntity.ok(new JwtResponse(newAccessToken, refreshToken, currentUser.getEmail(),
						currentUser.getFirstName(), currentUser.getEmail()));
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
		user.setRole("ROLE_USER"); 
		User created = userService.createUser(user);

		final UserDetails userDetails = userDetailsService.loadUserByUsername(created.getEmail());
		final String accessToken = jwtUtil.generateAccessToken(userDetails);
		final String refreshToken = jwtUtil.generateRefreshToken(userDetails);

		return ResponseEntity.ok(
				new JwtResponse(accessToken, refreshToken, created.getUsername(), created.getFirstName(), created.getEmail()));
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
		if (!UserSettingsService.hasPasswordLogin(currentUser)) {
			throw new BusinessLogicException("You sign in with Google, so there is no PlanMate password to change.",
					null, HttpStatus.BAD_REQUEST);
		}
		// 400, not 401: the app treats 401 as an expired login and would try to refresh it
		if (request.getCurrentPassword() == null
				|| !passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
			throw new BusinessLogicException("Your current password is incorrect.", null, HttpStatus.BAD_REQUEST);
		}
		String newPassword = request.getNewPassword();
		if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new BusinessLogicException("The new password needs at least " + MIN_PASSWORD_LENGTH + " characters.",
					null, HttpStatus.BAD_REQUEST);
		}
		if (newPassword.length() > MAX_PASSWORD_LENGTH) {
			throw new BusinessLogicException("The new password can be at most " + MAX_PASSWORD_LENGTH + " characters.",
					null, HttpStatus.BAD_REQUEST);
		}
		if (passwordEncoder.matches(newPassword, currentUser.getPassword())) {
			throw new BusinessLogicException("The new password is the same as your current one.", null,
					HttpStatus.BAD_REQUEST);
		}

		// Store only the hash (changePassword persists the value it is given)
		userService.changePassword(currentUser, passwordEncoder.encode(newPassword));

		return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
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

				// Only trust the email if Google has verified it; otherwise anyone could create a Google
				// account with someone else's address and log into their PlanMate account.
				if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
					logger.warn("Rejected Google login with unverified email");
					return new ResponseEntity<>("Your Google email address is not verified.", HttpStatus.UNAUTHORIZED);
				}

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
					user.setGoogleAccount(true);
					user.setRole("ROLE_USER");

					user = userService.createUser(user);
					logger.info("New user registered via Google SSO: " + email);
				}

				final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
				final String accessToken = jwtUtil.generateAccessToken(userDetails);
				final String refreshToken = jwtUtil.generateRefreshToken(userDetails);

				logger.info("Google SSO login successful for email: " + email);
				return ResponseEntity
						.ok(new JwtResponse(accessToken, refreshToken, user.getUsername(), user.getFirstName(), user.getEmail()));

			} else {
				return new ResponseEntity<>("Invalid Google token.", HttpStatus.UNAUTHORIZED);
			}
		} catch (Exception e) {
			logger.error("Google Auth Error: ", e);
			return new ResponseEntity<>("Failed to process Google authentication.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}