package com.planmate.dao;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

import com.planmate.dto.UserAvatar;

@Repository
public class AvatarDao {

	private final SessionFactory sessionFactory;

	public AvatarDao(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private Session session() {
		return sessionFactory.getCurrentSession();
	}

	public UserAvatar get(Long userId) {
		return session().get(UserAvatar.class, userId);
	}

	/** Stores the picture, replacing the user's previous one. */
	public void save(Long userId, byte[] image, java.time.Instant at) {
		UserAvatar avatar = get(userId);
		if (avatar == null) {
			session().persist(new UserAvatar(userId, image, at));
		} else {
			avatar.setImage(image);
			avatar.setUpdatedAt(at);
		}
	}

	public void delete(Long userId) {
		UserAvatar avatar = get(userId);
		if (avatar != null) {
			session().remove(avatar);
		}
	}
}
