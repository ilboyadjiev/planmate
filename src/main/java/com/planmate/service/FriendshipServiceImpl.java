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
	public Friendship createFriendship(Long userAId, Long userBId) {
		User userA = userService.getUserById(userAId);
		User userB = userService.getUserById(userBId);

		if (userA == null || userB == null) {
			logger.info("Can't create friendship: User(s) are not found: " + userAId + " or " + userBId);
			return null;
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
	public Friendship acceptFriendshipRequest(Long friendshipId, Long acceptingUserId) {
		if (acceptingUserId == null || friendshipId == null) {
			logger.info("Null parameters");
			return null;
		}

		Friendship friendship = friendshipDao.getById(friendshipId);
		if (friendship == null) {
			logger.info("Could not find a friend request with id " + friendshipId);
			return null;
		}

		if (ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(friendship.getStatus())) {
			logger.info("Friendship " + friendshipId + " is already accepted");
			return null;
		}

		if (!acceptingUserId.equals(friendship.getUserB().getId())) {
			logger.info("This user cannot accept this friend request.");
			return null;
		}

		if (ConstantsUtil.FRIENDSHIP_STATUS_PENDING.equalsIgnoreCase(friendship.getStatus())) {
			friendship.setAcceptDate(Timestamp.from(Instant.now()));
			friendship.setStatus(ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED);
			friendshipDao.update(friendship);
			logger.info("Friend request accepted: " + friendshipId);
			return friendship;
		}

		logger.info("Could not accept friend request. Check parameters.");
		return null;
	}

	@Transactional
	@Override
	public List<Friendship> getFriendshipList(Long userId) {
		return friendshipDao.getFriendshipsListByUserId(userId);
	}

	@Transactional
	@Override
	public Friendship getFriendshipById(Long id) {
		return friendshipDao.getById(id);
	}
}
