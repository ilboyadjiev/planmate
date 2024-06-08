package com.planmate.service;

import java.util.List;

import com.planmate.dto.User;

public interface UserService {

	List<User> getAllUsers();

	User getUserByEmail(String email);

	User getUserByUsername(String username);

	User createUser(User user);

	User updateUser(Long id, User user);

	Boolean checkEmailAvailable(String emailToCheck);

	Boolean checkUsernameAvailable(String usernameToCheck);
}
