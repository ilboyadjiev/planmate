package com.planmate.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import com.planmate.dto.UserSettings;

@Repository
public class UserSettingsDao {

	private final SessionFactory sessionFactory;

	public UserSettingsDao(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private Session session() {
		return sessionFactory.getCurrentSession();
	}

	/** The user's stored settings, or null when they never changed any. */
	public UserSettings get(Long userId) {
		return session().get(UserSettings.class, userId);
	}

	/** Stored settings of several users at once (users without a row are left out). */
	public List<UserSettings> getAll(Collection<Long> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return new ArrayList<>();
		}
		return session().createQuery("FROM UserSettings s WHERE s.userId IN (:ids)", UserSettings.class)
				.setParameterList("ids", userIds).list();
	}

	/** Whose calendar subscription link this is, or null. */
	public UserSettings getByCalendarKey(String key) {
		return session().createQuery("FROM UserSettings s WHERE s.calendarKey = :key", UserSettings.class)
				.setParameter("key", key).uniqueResult();
	}

	/** Stores the settings: a new row on the user's first change, else an update. */
	public void save(UserSettings settings) {
		session().merge(settings);
	}

	public void delete(Long userId) {
		UserSettings settings = get(userId);
		if (settings != null) {
			session().delete(settings);
		}
	}
}
