package com.planmate.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.User;
import com.planmate.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
//@Api(value = "User Management Interface", tags = "Users")
@Tag(name = "User Controller", description = "User Management Interface")
public class UserController {

	@Autowired
	private UserService userService;

	@PostMapping("/register")
	@Operation(summary = "Create a new user")
	public ResponseEntity<User> createUser(@RequestBody User user) {
		// validate

		// delegate
		User created = userService.createUser(user);
		if (created == null) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>(created, HttpStatus.CREATED);
	}

	@GetMapping("/all")
	public ResponseEntity<List<User>> getUsers(){
		List<User> allUsers = userService.getAllUsers();
		return new ResponseEntity<>(allUsers, HttpStatus.OK);
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
}
