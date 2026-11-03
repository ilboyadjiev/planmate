package com.planmate.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.util.AiSummaryResult;

@Service
public class EventMongoService {

	private final EventMongoRepository repository;
	private final MongoTemplate mongoTemplate;

	@Value("${gemini.api.key}")
    private String geminiApiKey;

	@Value("${gemini.api.model}")
	private String geminiApiModel;

	public EventMongoService(EventMongoRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	public EventDoc createEvent(EventDoc event) {
		return repository.save(event);
	}

	public List<EventDoc> getEventsByCreator(String creatorId) {
		return repository.findByCreatorId(creatorId);
	}

	public EventDoc getEventById(String id) {
		return repository.findById(id).orElseThrow(() -> new RuntimeException("EventDoc not found: " + id));
	}

	public EventDoc updateEvent(String id, EventDoc updatedData) {
		EventDoc existing = getEventById(id);
		existing.setTitle(updatedData.getTitle());
		existing.setIsPublic(updatedData.getIsPublic());
		existing.setDescription(updatedData.getDescription());
		existing.setStartTime(updatedData.getStartTime());
		existing.setEndTime(updatedData.getEndTime());
		existing.setTags(updatedData.getTags());
		existing.setLocation(updatedData.getLocation());
		existing.setParticipantIds(updatedData.getParticipantIds());
		// attendeeIds is left alone: only join/leave change it
		return repository.save(existing);
	}

	public void deleteEvent(String id) {
		repository.deleteById(id);
	}
	
	public List<EventDoc> getEventsByCreatorAndRange(String creatorId, Instant start, Instant end) {
        return repository.findByCreatorIdAndStartTimeBetween(creatorId, start, end);
    }

	/** Events the owner created plus, if participantId is given, events they were invited to or joined. */
	public List<EventDoc> getVisibleEventsInRange(String owner, String participantId, Instant start, Instant end) {
		if (participantId == null) {
			return getEventsByCreatorAndRange(owner, start, end);
		}
		return repository.findCreatedOrParticipatingInRange(owner, participantId, start, end);
	}
	
	/** Adds the user to the event's attendees. Atomic, so concurrent joins never overwrite each other. */
	public void addAttendee(String eventId, String userId) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)),
				new Update().addToSet("attendeeIds", userId), EventDoc.class);
	}

	public void removeAttendee(String eventId, String userId) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)),
				new Update().pull("attendeeIds", userId), EventDoc.class);
	}

	public void mergeGuestEvents(String guestId, String realUserId) {
		List<EventDoc> guestEvents = repository.findByCreatorId(guestId);
		if (guestEvents.isEmpty()) {
			return;
		}
		guestEvents.forEach(event -> event.setCreatorId(realUserId));
		repository.saveAll(guestEvents);
	}

    public AiSummaryResult getAiSummary(String userEmail) {
        List<EventDoc> upcomingEvents = getEventsByCreatorAndRange(userEmail, Instant.now(), Instant.now().plusSeconds(48 * 60 * 60));
        if (upcomingEvents.isEmpty()) {
            return new AiSummaryResult("You have a completely free schedule ahead! Enjoy your free time.", false);
        }

        String eventListText = upcomingEvents.stream()
                .map(e -> e.toSimplifiedString())
                .collect(Collectors.joining("\n"));

        String prompt = "You are a helpful personal assistant. The current time is " + Instant.now().toString() + ". You have access to the user's upcoming events for the next 48 hours. " +
                "Review these upcoming calendar events and provide a friendly, concise, 2-to-3 sentence briefing summary:\n\n" + 
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

            return new AiSummaryResult(response.text(), true);

        } catch (Exception e) {
            e.printStackTrace();
            return new AiSummaryResult("I couldn't generate your briefing right now, but you have " + upcomingEvents.size() + " events coming up today.", true);
        }
    }
}
