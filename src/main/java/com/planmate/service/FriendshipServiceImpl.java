package com.planmate.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.planmate.dao.FriendshipDao;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.util.ConstantsUtil;

@Service
public class FriendshipServiceImpl implements FriendshipService {

	private static final Logger logger = LoggerFactory.getLogger(FriendshipServiceImpl.class);

	@Autowired
	private FriendshipDao friendshipDao;

	@Autowired
	private UserService userService;

	@Transactional
	@Override
	public Friendship createFriendship(String requesterEmail, Long userBId) {
		User userA = userService.getUserByEmail(requesterEmail);
		User userB = userService.getUserById(userBId);

		if (userA == null || userB == null) {
			throw new IllegalArgumentException("One or both users do not exist.");
		}

		if (userA.getId().equals(userB.getId())) {
			throw new IllegalArgumentException("You cannot send a friend request to yourself.");
		}
		

		if (friendshipDao.friendshipExists(userA, userB)) {
			throw new IllegalArgumentException("A friend request already exists between these users.");
		}
		

		Friendship friendship = new Friendship();
		friendship.setUserA(userA);
		friendship.setUserB(userB);
		friendship.setCreationDate(Timestamp.from(Instant.now()));
		friendship.setRequestDate(Timestamp.from(Instant.now()));
		friendship.setStatus(ConstantsUtil.FRIENDSHIP_STATUS_PENDING);
		
		return friendshipDao.create(friendship);
	}

	@Transactional
	@Override
	public Friendship acceptFriendshipRequest(Long friendshipId, String acceptingUserEmail) {
		User acceptingUser = userService.getUserByEmail(acceptingUserEmail);
		if (acceptingUser == null) {
			throw new IllegalArgumentException("Accepting user not found.");
		}

		Friendship friendship = friendshipDao.getById(friendshipId);
		if (friendship == null) {
			throw new IllegalArgumentException("Could not find a friend request with id " + friendshipId);
		}

		if (ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(friendship.getStatus())) {
			throw new IllegalArgumentException("Friendship is already accepted.");
		}

		// Security check: Only the targeted user can accept it
		if (!acceptingUser.getId().equals(friendship.getUserB().getId())) {
			throw new IllegalArgumentException("You are not authorized to accept this friend request.");
		}

		if (ConstantsUtil.FRIENDSHIP_STATUS_PENDING.equalsIgnoreCase(friendship.getStatus())) {
			friendship.setAcceptDate(Timestamp.from(Instant.now()));
			friendship.setStatus(ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED);
			friendshipDao.update(friendship);
			
			logger.info("Friend request accepted: " + friendshipId);
			return friendship;
		}

		throw new IllegalArgumentException("Invalid friendship status.");
	}

	@Transactional
	@Override
	public List<Friendship> getFriendshipList(String userEmail) {
		return friendshipDao.getFriendshipsListByUserEmail(userEmail);
	}

	@Transactional
	@Override
	public Friendship getFriendshipById(Long id) {
		return friendshipDao.getById(id);
	}

	@Transactional
	@Override
	public Object deleteFriendship(Friendship friendship) {
		return friendshipDao.delete(friendship);
	}
}
