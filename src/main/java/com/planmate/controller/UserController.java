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
import com.planmate.service.EventMongoService;
import com.planmate.service.FriendGroupService;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;
import com.planmate.util.PublicViews;

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

	@Autowired
	private EventMongoService eventMongoService;

	@Autowired
	private FriendGroupService friendGroupService;

	@PostMapping("/register")
	@Operation(summary = "Create a new user")
	public ResponseEntity<String> createUser(@RequestBody User user) {
		return new ResponseEntity<>("Please use /api/v1/auth/register", HttpStatus.METHOD_NOT_ALLOWED);
	}

	/** True if the user with this id is the caller. Every per-user endpoint must check this. */
	private boolean isSelf(Long id, Principal principal) {
		// Fail closed: a missing name must never match (Objects.equals(null, null) would grant access)
		String callerEmail = principal == null ? null : principal.getName();
		if (callerEmail == null) {
			return false;
		}
		User target = userService.getUserById(id);
		return target != null && callerEmail.equals(target.getEmail());
	}

	/** Returns the caller's own profile. The optional email parameter is ignored (kept for older clients). */
	@GetMapping("/profile")
	public ResponseEntity<User> getOwnProfile(Principal principal) {
		User foundUser = userService.getUserByEmail(principal.getName());
		if (foundUser == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(foundUser, HttpStatus.OK);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update an existing user. Missing field values are overwritten.")
	public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user, Principal principal) {
		if (!isSelf(id, principal)) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		try {
			User updatedUser = userService.updateUser(id, user, false);
			if (updatedUser == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			return new ResponseEntity<>(updatedUser, HttpStatus.OK);
		} catch (RuntimeException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Update an existing user. Updates only provided fields.")
	public ResponseEntity<User> patchUser(@PathVariable Long id, @RequestBody User user, Principal principal) {
		if (!isSelf(id, principal)) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		try {
			User patchedUser = userService.updateUser(id, user, true);
			if (patchedUser == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			return new ResponseEntity<>(patchedUser, HttpStatus.OK);
		} catch (RuntimeException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/{id}/friends")
	public ResponseEntity<List<Map<String, Object>>> getFriendsListByUserId(@PathVariable Long id, Principal principal) {
		if (!isSelf(id, principal)) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		User user = userService.getUserById(id);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		List<Friendship> friends = friendshipService.getFriendshipList(user.getEmail());
		return new ResponseEntity<>(PublicViews.friendships(friends), HttpStatus.OK);
	}

	@GetMapping("/search")
	public ResponseEntity<List<Map<String, Object>>> searchUsernames(@RequestParam String term,
			@RequestHeader("Authorization") String authorizationHeader) {
		// Retrieve the currently logged in user
		String username = "";
		if (authorizationHeader != null) {
			String jwtToken = authorizationHeader.replace("Bearer ", "");
			username = jwtUtil.extractUsername(jwtToken);
		} else {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		// Name and username only: search must not reveal anyone's email or contact details
		List<User> results = userService.searchUsernames(username, term);
		return new ResponseEntity<>(PublicViews.people(results), HttpStatus.OK);
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

		if (!isSelf(id, principal)) {

			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("error", "Security violation: You can only delete your own account."));
		}

		try {
			// Group memberships reference the user row, so they go first
			friendGroupService.removeFromAllGroups(targetUser.getId());
			userService.deleteUser(targetUser);
			// Calendar data lives in MongoDB: the user's events, and their place on other people's events
			eventMongoService.removeUserData(targetUser.getEmail(), String.valueOf(targetUser.getId()));
			return ResponseEntity.ok(Map.of("message", "Account and all associated data successfully deleted."));

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to delete account due to a server error."));
		}
	}
}
