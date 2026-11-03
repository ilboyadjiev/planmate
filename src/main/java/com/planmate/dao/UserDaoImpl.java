package com.planmate.dao;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.planmate.dto.User;

@Repository
public class UserDaoImpl implements UserDAO {

	@Autowired
	private SessionFactory sessionFactory;

	@SuppressWarnings("unchecked")
	@Override
	public List<User> getAllUsers(){
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM User u";
		return session.createQuery(hql).list();
	}

	@Override
	public User getUserById(Long id) {
		Session currentSession = sessionFactory.getCurrentSession();
		return currentSession.get(User.class, id);
	}
	
	@Override
	public User getUserByEmail(String email) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM User u WHERE u.email = :email";
		return (User) session.createQuery(hql).setParameter("email", email).uniqueResult();
	}
	
	@Override
	public User getUserByUsername(String username) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM User u WHERE u.username = :username";
		return (User) session.createQuery(hql).setParameter("username", username).uniqueResult();
	}

	@Override
	public User createUser(User user) {
		return updateUser(user);
	}

	@Override
	public User updateUser(User user) {
		Session session = sessionFactory.getCurrentSession();
		session.saveOrUpdate(user);
		return user;
	}

	@Override
	public boolean isEmailDuplicate(String email) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "SELECT COUNT(u.email) FROM User u WHERE u.email = :email";
		Long count = (Long) session.createQuery(hql).setParameter("email", email).uniqueResult();
		return count > 0;
	}

	@Override
	public boolean isUsernameDuplicate(String username) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "SELECT COUNT(u.username) FROM User u WHERE u.username = :username";
		Long count = (Long) session.createQuery(hql).setParameter("username", username).uniqueResult();
		return count > 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	/**
	 * People whose username, first name, last name or full name contains the text (any case), except the viewer.
	 * Never matches emails. viewerEmail is the caller (the JWT subject).
	 */
	public List<User> searchUsernames(String viewerEmail, String searchText) {
		Session session = sessionFactory.getCurrentSession();
		String like = "%" + searchText.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		String hql = "FROM User u WHERE u.email <> :viewer AND (lower(u.username) LIKE :t OR lower(u.firstName) LIKE :t "
				+ "OR lower(u.lastName) LIKE :t OR lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) LIKE :t) "
				+ "ORDER BY u.username";
		List<User> users = session.createQuery(hql).setParameter("viewer", viewerEmail).setParameter("t", like)
				.setMaxResults(50).list();
		return users;
	}

	@Override
	public void deleteUserById(Long id) {
		Session session = sessionFactory.getCurrentSession();
		User userToDelete = session.get(User.class, id);
		if (userToDelete != null) {
			session.delete(userToDelete);
		}
	}
}
