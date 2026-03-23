package com.planmate.service;

import java.util.List;

import com.planmate.dto.Friendship;

public interface FriendshipService {
	
	Friendship createFriendship(String requesterEmail, Long userBId);
	
	Friendship acceptFriendshipRequest(Long friendshipId, String acceptingUserEmail);

	Friendship getFriendshipById(Long id);

	List<Friendship> getFriendshipList(String userEmail);

	Object deleteFriendship(Friendship friendship);
}
