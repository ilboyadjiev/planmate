package com.planmate.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
//@Api(value = "User Management Interface", tags = "Users")
@Tag(name = "User Controller", description = "User Management Interface")
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private UserService userService;

	@Autowired
	private FriendshipService friendshipService;

	@PostMapping("/register")
	@Operation(summary = "Create a new user")
	public ResponseEntity<String> createUser(@RequestBody User user) {
		return new ResponseEntity<>("Please use /api/v1/auth/register", HttpStatus.METHOD_NOT_ALLOWED);
	}

	@GetMapping("/all")
	public ResponseEntity<List<User>> getUsers() {
		List<User> allUsers = userService.getAllUsers();
		logger.info("Getting all users.");
		return new ResponseEntity<>(allUsers, HttpStatus.OK);
	}

	@GetMapping("/{email}")
	public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
		User foundUser = userService.getUserByEmail(email);
		return new ResponseEntity<>(foundUser, HttpStatus.OK);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update an existing user. Missing field values are overwritten.")
	public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
		try {
			User updatedUser = userService.updateUser(id, user);
			return new ResponseEntity<>(updatedUser, HttpStatus.OK);
		} catch (RuntimeException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Update an existing user. Updates only provided fields.")
	public ResponseEntity<User> patchUser(@PathVariable Long id, @RequestBody User user) {
		try {
			User patchedUser = userService.updateUser(id, user);
			return new ResponseEntity<>(patchedUser, HttpStatus.OK);
		} catch (RuntimeException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/{id}/friends")
	public ResponseEntity<List<Friendship>> getFriendsListByUserId(@PathVariable Long id) {
		User user = userService.getUserById(id);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		List<Friendship> friends = friendshipService.getFriendshipList(user.getEmail());
		return new ResponseEntity<>(friends, HttpStatus.OK);
	}

	@GetMapping("/search")
	public ResponseEntity<List<User>> searchUsernames(@RequestParam String term,
			@RequestHeader("Authorization") String authorizationHeader) {
		// Retrieve the currently logged in user
		String username = "";
		if (authorizationHeader != null) {
			String jwtToken = authorizationHeader.replace("Bearer ", "");
			username = jwtUtil.extractUsername(jwtToken);
		} else {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		List<User> results = userService.searchUsernames(username, term);
		return new ResponseEntity<>(results, HttpStatus.OK);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteAccount(@PathVariable Long id, Principal principal) {

		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "You must be logged in to do this."));
		}

		User targetUser = userService.getUserById(id);

		if (targetUser == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
		}

		String authenticatedUsername = principal.getName();

		if (!targetUser.getEmail().equals(authenticatedUsername)
				&& !targetUser.getUsername().equals(authenticatedUsername)) {

			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("error", "Security violation: You can only delete your own account."));
		}

		try {
			userService.deleteUser(targetUser);
			return ResponseEntity.ok(Map.of("message", "Account and all associated data successfully deleted."));

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to delete account due to a server error.", "details", e.getMessage()));
		}
	}
}
