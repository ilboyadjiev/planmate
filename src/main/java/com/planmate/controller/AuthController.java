package com.planmate.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.User;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;

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

	@PostMapping("/authenticate")
	public ResponseEntity<?> createAuthenticationToken(@RequestBody User user) throws Exception {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Incorrect username or password");
		}

		// email is used for auth
		final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
		final String jwt = jwtUtil.generateToken(userDetails.getUsername());

		Map<String, String> response = new HashMap<>();
		response.put("jwt", jwt);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestBody User user) {
		if (userService.getUserByEmail(user.getEmail()) != null) {
			return ResponseEntity.badRequest().body("Email already in use");
		}

		user.setPassword(passwordEncoder.encode(user.getPassword()));
		user.setRole("ROLE_USER"); // default role
		User created = userService.createUser(user);

		// generate jwt token
		final String jwt = jwtUtil.generateToken(created.getUsername());
		Map<String, String> response = new HashMap<>();
		response.put("jwt", jwt);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/check-email-exists/{emailToCheck}")
	public ResponseEntity<?> checkEmailAvailable(@PathVariable String emailToCheck){
		logger.info("Checking available email: " + emailToCheck);
		boolean emailAvailable = Boolean.TRUE.equals(userService.checkEmailAvailable(emailToCheck));
		Map<String, Boolean> response = new HashMap<>();
		response.put("available", emailAvailable);
		logger.info("Email " + emailToCheck + " is " + (emailAvailable ? "available" : "not available"));
		return new ResponseEntity<>(response, emailAvailable ? HttpStatus.OK : HttpStatus.CONFLICT);
	}

	@GetMapping("/check-username-exists/{usernameToCheck}")
	public ResponseEntity<?> checkUsernameAvailable(@PathVariable String usernameToCheck){
		boolean usernameAvailable = Boolean.TRUE.equals(userService.checkUsernameAvailable(usernameToCheck));
		Map<String, Boolean> response = new HashMap<>();
		response.put("available", usernameAvailable);
		return new ResponseEntity<>(response, usernameAvailable ? HttpStatus.OK : HttpStatus.CONFLICT);
	}
}