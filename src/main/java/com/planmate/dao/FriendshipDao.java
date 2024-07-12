package com.planmate.dao;

import java.util.List;

import com.planmate.dto.Friendship;

public interface FriendshipDao {

	Friendship create(Friendship friendship);

	Friendship getById(Long id);

	List<Friendship> getFriendshipsListByUserId(Long id);

	Friendship update(Friendship friendship);

	Friendship delete(Friendship friendship);
}
