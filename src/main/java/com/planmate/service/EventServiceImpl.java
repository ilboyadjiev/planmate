package com.planmate.service;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventDAO;
import com.planmate.dto.Event;

@Service
public class EventServiceImpl implements EventService {

	@Autowired
	private EventDAO eventDAO;

	@Transactional
	@Override
	public List<Event> getAllEvents() {
		return eventDAO.getAllEvents();
	}

	@Transactional
	@Override
	public List<Event> getAllEventsForUser(final String user) {
		return eventDAO.getAllEventsForUser(user);
	}

	@Transactional
	@Override
	public Event getEventById(final Long id) {
		return eventDAO.getEventById(id);
	}

	@Transactional
	@Override
	public Event createNewEvent(Event event) {
		return eventDAO.createNewEvent(event);
	}

	@Transactional
	@Override
	public Event updateEvent(Event event) {
		return eventDAO.updateEvent(event);
	}

	@Transactional
	@Override
	public boolean deleteEvent(Event event) {
		return eventDAO.deleteEvent(event);
	}

}
