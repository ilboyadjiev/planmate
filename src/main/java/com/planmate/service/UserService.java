package com.planmate.service;

import java.util.List;

import com.planmate.dto.User;

public interface UserService {

	List<User> getAllUsers();

	User createUser(User user);

	User updateUser(Long id, User user);
}
