package com.planmate.service;

import java.util.List;

import com.planmate.dto.Friendship;

public interface FriendshipService {
	
	Friendship createFriendship(String requesterEmail, Long userBId);
	
	Friendship acceptFriendshipRequest(Long friendshipId, String acceptingUserEmail);

	Friendship getFriendshipById(Long id);

	List<Friendship> getFriendshipList(String userEmail);

	Object deleteFriendship(Friendship friendship);

	/**
	 * Ends a friendship or a pending request: declining a request you received, cancelling one you sent, or
	 * unfriending. Only the two people in it may do this. Returns false when there is no such friendship for them.
	 */
	boolean removeFriendship(Long friendshipId, String callerEmail);
}
