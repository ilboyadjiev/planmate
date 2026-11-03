package com.planmate.dao;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import com.planmate.dto.FriendGroup;

@Repository
public class FriendGroupDao {

	private final SessionFactory sessionFactory;

	public FriendGroupDao(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private Session session() {
		return sessionFactory.getCurrentSession();
	}

	public FriendGroup getById(Long id) {
		return session().get(FriendGroup.class, id);
	}

	/** Groups the user is a member of, alphabetically. */
	public List<FriendGroup> getGroupsOfMember(Long userId) {
		return session().createQuery(
				"SELECT DISTINCT g FROM FriendGroup g JOIN g.members m WHERE m.id = :userId ORDER BY g.name",
				FriendGroup.class).setParameter("userId", userId).list();
	}

	public FriendGroup save(FriendGroup group) {
		session().saveOrUpdate(group);
		return group;
	}

	public void delete(FriendGroup group) {
		session().delete(group);
	}
}
