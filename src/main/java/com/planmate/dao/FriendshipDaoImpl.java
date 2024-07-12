package com.planmate.dao;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.planmate.dto.Friendship;

@Repository
public class FriendshipDaoImpl implements FriendshipDao {

	@Autowired
	private SessionFactory sessionFactory;

	@Override
	public Friendship create(Friendship friendship) {
		return update(friendship);
	}

	@Override
	public Friendship getById(Long id) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM Friendship f WHERE f.id = :id";
		return (Friendship) session.createQuery(hql).setParameter("id", id).uniqueResult();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Friendship> getFriendshipsListByUserId(Long id) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM Friendship f WHERE f.userA.id = :id OR f.userB.id = :id";
		return session.createQuery(hql).setParameter("id", id).list();
	}

	@Override
	public Friendship update(Friendship friendship) {
		Session session = sessionFactory.getCurrentSession();
		session.saveOrUpdate(friendship);
		return friendship;
	}

	@Override
	public Friendship delete(Friendship friendship) {
		Session session = sessionFactory.getCurrentSession();
		session.delete(friendship);
		return friendship;
	}

}
