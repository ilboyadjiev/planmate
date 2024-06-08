package com.planmate.dao;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.planmate.dto.Event;

@Repository
public class EventDaoImpl implements EventDAO {

	@Autowired
	private SessionFactory sessionFactory;

	@SuppressWarnings("unchecked")
	@Override
	public List<Event> getAllEvents() {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM Event e";
		return session.createQuery(hql).list();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Event> getAllEventsForUser(final String user) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM Event e WHERE e.user.email = :user ";
		return session.createQuery(hql).setParameter("user", user).list();
	}

	@Override
	public Event getEventById(final Long id) {
		Session session = sessionFactory.getCurrentSession();
		String hql = "FROM Event e WHERE e.id = :id ";
		return (Event) session.createQuery(hql).setParameter("id", id).uniqueResult();
	}

	@Override
	public Event createNewEvent(Event event, String createdBy) {
		//EntityUtil.fillAbstractEntityAttributes(event);
		return updateEvent(event);
	}

	@Override
	public Event updateEvent(Event event) {
		Session session = sessionFactory.getCurrentSession();
		session.saveOrUpdate(event);
		return event;
	}

	@Override
	public boolean deleteEvent(Event event) {
		if (event != null) {
			event.setDeleted(true);
			updateEvent(event);
			return true;
		}
		return false;
	}

}
