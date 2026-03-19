package com.planmate.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.planmate.dto.Friendship;
import com.planmate.service.FriendshipService;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {

	@Autowired
	private FriendshipService friendshipService;

	@PostMapping("/request/{userBId}")
	public ResponseEntity<?> createNewFriendRequest(@PathVariable Long userBId, Principal principal) {
		try {
			Friendship created = friendshipService.createFriendship(principal.getName(), userBId);
			return new ResponseEntity<>(created, HttpStatus.CREATED);
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PutMapping("/accept/{friendshipId}")
	public ResponseEntity<?> acceptFriendRequest(@PathVariable Long friendshipId, Principal principal) {
		try {
			Friendship accepted = friendshipService.acceptFriendshipRequest(friendshipId, principal.getName());
			return new ResponseEntity<>(accepted, HttpStatus.OK);
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/list")
	public ResponseEntity<?> getUserFriendships(Principal principal) {
		try {
			String currentUserEmail = principal.getName();
			
			List<Friendship> friendships = friendshipService.getFriendshipList(currentUserEmail);
			
			return new ResponseEntity<>(friendships, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
}