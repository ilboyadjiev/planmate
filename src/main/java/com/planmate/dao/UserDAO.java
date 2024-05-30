package com.planmate.dao;

import java.util.List;

import com.planmate.dto.User;

public interface UserDAO {

	List<User> getAllUsers();

	User getUserById(Long id);

	User createUser(User user);
	
	User updateUser(User user);

	boolean isEmailDuplicate(String email);
}
