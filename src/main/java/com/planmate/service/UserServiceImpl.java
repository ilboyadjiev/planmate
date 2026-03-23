package com.planmate.service;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventDAO;
import com.planmate.dao.FriendshipDao;
import com.planmate.dao.UserDAO;
import com.planmate.dto.User;
import com.planmate.exception.BusinessLogicException;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UserDAO userDAO;

	@Autowired
	private FriendshipDao friendshipDao;

	@Autowired
	private EventDAO eventDAO;

	@Transactional
	@Override
	public List<User> getAllUsers() {
		return userDAO.getAllUsers();
	}

	@Transactional
	@Override
	public User createUser(User user) {
		// check if email is in use
		if (userDAO.isEmailDuplicate(user.getEmail())) {
			throw new BusinessLogicException("Email address is already in use: " + user.getEmail(), null,
					HttpStatus.CONFLICT);
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
		user.setPassword(existing.getPassword()); // password is not updated here
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

	@Transactional
	@Override
	public User getUserById(Long id) {
		return userDAO.getUserById(id);
	}

	@Transactional
	@Override
	public Boolean checkEmailAvailable(String emailToCheck) {
		return !userDAO.isEmailDuplicate(emailToCheck);
	}

	@Transactional
	@Override
	public Boolean checkUsernameAvailable(String usernameToCheck) {
		return !userDAO.isUsernameDuplicate(usernameToCheck);
	}

	@Transactional
	@Override
	public List<User> searchUsernames(String user, String searchText) {
		return userDAO.searchUsernames(user, searchText);
	}

	@Transactional
	@Override
	public void changePassword(User currentUser, String newPassword) {
		currentUser.setPassword(newPassword);
		userDAO.updateUser(currentUser);
	}

	@Transactional
	@Override
	public void deleteUser(User user) {
		// fetch the user to ensure we have all associations loaded
		User managedUser = getUserById(user.getId());

		try {
			// delete all events created by the user and remove the user from participating events
			eventDAO.getAllEventsForUsername(managedUser.getUsername()).forEach(event -> eventDAO.deleteEvent(event));
			
			if (managedUser.getParticipatingEvents() != null) {
				managedUser.getParticipatingEvents().forEach(event -> {
					event.getParticipants().remove(managedUser);
					eventDAO.updateEvent(event);
				});
			}
			
			// delete all friendships of the user
			friendshipDao.getFriendshipsListByUserEmail(managedUser.getEmail())
					.forEach(friendship -> friendshipDao.delete(friendship));
			
			// delete the user
			userDAO.deleteUserById(managedUser.getId());
		} catch (Exception e) {
			throw new BusinessLogicException("User " + user.getEmail() + " could not be deleted", null,
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

	}

}
