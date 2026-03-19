package com.planmate.service;

import java.sql.Timestamp;
import java.util.List;

import com.planmate.dto.Event;

public interface EventService {

	List<Event> getAllEvents();

	List<Event> getAllEventsForUser(final String user);
	
	List<Event> getAllEventsForUsername(final String username);

	Event getEventById(final Long id);

	Event createNewEvent(Event event, String createdBy);

	Event updateEvent(Long id, Event event, Event incomingEvent);

	boolean deleteEvent(Event event);

	List<Event> findByUserEmailAndStartTimeBetween(String currentUserEmail, Timestamp startTime, Timestamp endTime);
}
