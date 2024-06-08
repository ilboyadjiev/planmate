package com.planmate.service;

import java.util.List;

import com.planmate.dto.Event;

public interface EventService {

	List<Event> getAllEvents();

	List<Event> getAllEventsForUser(final String user);

	Event getEventById(final Long id);

	Event createNewEvent(Event event, String createdBy);

	Event updateEvent(Long id, Event event);

	boolean deleteEvent(Event event);
}
