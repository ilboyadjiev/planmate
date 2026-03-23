package com.planmate.dao;

import java.util.List;

import com.planmate.dto.Friendship;
import com.planmate.dto.User;

public interface FriendshipDao {

	Friendship create(Friendship friendship);

	Friendship getById(Long id);

	List<Friendship> getFriendshipsListByUserEmail(String userEmail);

	Friendship update(Friendship friendship);

	Friendship delete(Friendship friendship);

	boolean friendshipExists(User userA, User userB);
}
