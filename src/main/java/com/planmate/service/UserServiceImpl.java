package com.planmate.service;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.planmate.dao.UserDAO;
import com.planmate.dto.User;
import com.planmate.exception.BusinessLogicException;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UserDAO userDAO;

	@Transactional
	@Override
	public List<User> getAllUsers(){
		return userDAO.getAllUsers();
	}

	@Transactional
	@Override
	public User createUser(User user) {
		// check if email is in use
		if (userDAO.isEmailDuplicate(user.getEmail())) {
			throw new BusinessLogicException("Email address is already in use: " + user.getEmail(), null, HttpStatus.CONFLICT);
		}
		return userDAO.createUser(user);
	}

	@Transactional
	@Override
	public User updateUser(Long id, User user) {
		User existing = userDAO.getUserById(id);
		if (existing == null) {
			// not found
			return null;
		}
		existing.setFirstName(user.getFirstName());
		return userDAO.updateUser(existing);
	}

	@Transactional
	@Override
	public User getUserByEmail(String email) {
		return userDAO.getUserByEmail(email);
	}

	@Transactional
	@Override
	public User getUserByUsername(String username) {
		return userDAO.getUserByUsername(username);
	}

}
