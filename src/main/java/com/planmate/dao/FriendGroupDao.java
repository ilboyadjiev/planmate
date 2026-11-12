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
		// Hibernate 7 removed saveOrUpdate; merge upserts and returns the managed instance.
		return session().merge(group);
	}

	public void delete(FriendGroup group) {
		// Hibernate 7 removed delete; remove needs a managed instance, so merge first if the argument is detached.
		session().remove(session().contains(group) ? group : session().merge(group));
	}
}
