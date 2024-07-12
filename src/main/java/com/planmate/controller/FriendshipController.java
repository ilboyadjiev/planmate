package com.planmate.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;
import com.planmate.util.JwtUtil;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {
    
	@Autowired
    private JwtUtil jwtUtil;

	@Autowired
	private UserService userService;

	@Autowired
	private FriendshipService friendshipService;

	@PostMapping("/request/{userBId}")
	public ResponseEntity<Friendship> createNewFriendRequest(@PathVariable Long userBId, @RequestHeader("Authorization") String authorizationHeader) {
        // Retrieve the currently logged in user
		String email = "";
		if (authorizationHeader != null) {
			String jwtToken = authorizationHeader.replace("Bearer ", "");
			email = jwtUtil.extractUsername(jwtToken);
		} else {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		User userA = userService.getUserByEmail(email);
		User userB = userService.getUserById(userBId);
		if (userA == null || userB == null) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		Friendship created = friendshipService.createFriendship(userA.getId(), userB.getId());

		return new ResponseEntity<>(created, HttpStatus.OK);
	}

	@PutMapping("/accept/{friendshipId}")
	public ResponseEntity<Friendship> acceptFriendRequest(@PathVariable Long friendshipId, @RequestHeader("Authorization") String authorizationHeader) {

		Friendship friendship = friendshipService.getFriendshipById(friendshipId);
		if (friendship == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
        // Retrieve the currently logged in user
		String email = "";
		if (authorizationHeader != null) {
			String jwtToken = authorizationHeader.replace("Bearer ", "");
			email = jwtUtil.extractUsername(jwtToken);
		} else {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		User userB = userService.getUserByEmail(email);
		User userA = friendship.getUserA();
		if (userA == null || userB == null) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		Friendship accepted = friendshipService.acceptFriendshipRequest(friendship.getId(), userB.getId());

		return new ResponseEntity<>(accepted, HttpStatus.OK);
	}
}
