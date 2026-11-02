package com.planmate.service;

import java.util.List;

import com.planmate.dto.User;

public interface UserService {

	List<User> getAllUsers();

	User getUserByEmail(String email);

	User getUserByUsername(String username);

	User getUserById(Long id);

	User createUser(User user);

	/**
	 * Updates the editable profile fields (first/last name and contact data). Email, username, password and role
	 * are never changed here. With partial=true, null fields in the request leave the stored value untouched.
	 * Returns null if the user does not exist.
	 */
	User updateUser(Long id, User user, boolean partial);
	
	Boolean checkEmailAvailable(String emailToCheck);

	Boolean checkUsernameAvailable(String usernameToCheck);

	List<User> searchUsernames(String user, String searchText);

	void changePassword(User currentUser, String newPassword);

	void deleteUser(User user);
}
