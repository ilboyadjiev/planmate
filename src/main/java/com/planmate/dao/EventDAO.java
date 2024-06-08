package com.planmate.dao;

import java.util.List;

import com.planmate.dto.Event;

public interface EventDAO {

	List<Event> getAllEvents();

	List<Event> getAllEventsForUser(final String user);

	Event getEventById(final Long id);

	Event createNewEvent(Event event, String createdBy);

	Event updateEvent(Event event);

	boolean deleteEvent(Event event);

}
