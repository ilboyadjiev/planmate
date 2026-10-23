package com.planmate.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.planmate.dao.EventDAO;
import com.planmate.dto.Event;
import com.planmate.dto.User;

@Service
public class EventServiceImpl implements EventService {

	@Autowired
	private EventDAO eventDAO;

	@Autowired
	private UserService userService;

	@Value("${gemini.api.key}")
    private String geminiApiKey;

	@Value("${gemini.api.model}")
	private String geminiApiModel;
	
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

	@Transactional
	@Override
    public String getAiSummary(String userEmail) {
        Timestamp startTime = Timestamp.from(Instant.now());
        Timestamp endTime = Timestamp.from(Instant.now().plusSeconds(48 * 60 * 60));
        
        List<Event> upcomingEvents = findByUserEmailAndStartTimeBetween(userEmail, startTime, endTime);

        if (upcomingEvents.isEmpty()) {
            return "You have a completely free schedule ahead! Enjoy your free time.";
        }

        String eventListText = upcomingEvents.stream()
                .map(e -> e.toSimplifiedString())
                .collect(Collectors.joining("\n"));

        String prompt = "You are a helpful personal assistant for a busy software developer. The current time is " + startTime + ". You have access to the user's upcoming events for the next 48 hours. " +
                "Review these upcoming calendar events and provide a friendly, concise, 2-to-3 sentence morning briefing summary:\n\n" + 
                eventListText;
        prompt += "\n\nPlease provide tips on how to prepare for the events, what to wear and when to leave home.";

        try {
            Client client = Client.builder()
                                  .apiKey(geminiApiKey)
                                  .build();

            GenerateContentResponse response = client.models.generateContent(
                    geminiApiModel, 
                    prompt, 
                    null
            );

            return response.text();

        } catch (Exception e) {
            e.printStackTrace();
            return "I couldn't generate your briefing right now, but you have " + upcomingEvents.size() + " events coming up today.";
        }
    }

}
