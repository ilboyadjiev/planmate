package com.planmate.service;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventDAO;
import com.planmate.dto.Event;
import com.planmate.dto.User;

@Service
public class EventServiceImpl implements EventService {

	@Autowired
	private EventDAO eventDAO;

	@Autowired
	private UserService userService;

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
	public List<Event> getAllEventsForUsername(final String username) {
		return eventDAO.getAllEventsForUsername(username);
	}

	@Transactional
	@Override
	public Event getEventById(final Long id) {
		return eventDAO.getEventById(id);
	}

	@Transactional
	@Override
	public Event createNewEvent(Event event, String createdBy) {
		User creator = userService.getUserByEmail(createdBy);
		if (creator != null) {
			event.setUser(creator);
		}
		
		if (event.getParticipantIds() != null && !event.getParticipantIds().isEmpty()) {
			Set<User> invitedFriends = new HashSet<>();
			for (Long friendId : event.getParticipantIds()) {
				User friend = userService.getUserById(friendId); 
				if (friend != null) {
					invitedFriends.add(friend);
				}
			}
			event.setParticipants(invitedFriends);
		}
		
		return eventDAO.createNewEvent(event, createdBy);
	}

	@Transactional
	@Override
	public Event updateEvent(Long id, Event existingEvent, Event incomingEvent) {
		
		if (existingEvent != null) {
			// Only update the fields that are actually editable by the user. We don't want to allow changing the owner of the event, for example.
			existingEvent.setTitle(incomingEvent.getTitle());
			existingEvent.setDescription(incomingEvent.getDescription());
			existingEvent.setStartTime(incomingEvent.getStartTime());
			existingEvent.setEndTime(incomingEvent.getEndTime());
			
			if (incomingEvent.getParticipantIds() != null) {
				Set<User> updatedFriends = new HashSet<>();
				
				for (Long friendId : incomingEvent.getParticipantIds()) {
					User friend = userService.getUserById(friendId); 
					if (friend != null) {
						updatedFriends.add(friend);
					}
				}
				existingEvent.setParticipants(updatedFriends);
			}
			
			return eventDAO.updateEvent(existingEvent);
		}
		
		return null;
	}

	@Transactional
	@Override
	public boolean deleteEvent(Event event) {
		return eventDAO.deleteEvent(event);
	}

	@Transactional
	@Override
	public List<Event> findByUserEmailAndStartTimeBetween(String currentUserEmail, Timestamp startTime,
			Timestamp endTime) {
		return eventDAO.getEventsInRange(currentUserEmail, startTime, endTime);
	}

}
