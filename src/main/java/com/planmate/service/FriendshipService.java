package com.planmate.service;

import java.util.List;

import com.planmate.dto.Friendship;

public interface FriendshipService {

	Friendship getFriendshipById(Long id);

	Friendship createFriendship(Long userAId, Long userBId);

	Friendship acceptFriendshipRequest(Long friendshipId, Long acceptingUserId);

	List<Friendship> getFriendshipList(Long userId);
}
