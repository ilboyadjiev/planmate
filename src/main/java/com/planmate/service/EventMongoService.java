package com.planmate.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.util.AiSummaryResult;

@Service
public class EventMongoService {

	private final EventMongoRepository repository;

	@Value("${gemini.api.key}")
    private String geminiApiKey;

	@Value("${gemini.api.model}")
	private String geminiApiModel;

	public EventMongoService(EventMongoRepository repository) {
		this.repository = repository;
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
		return repository.save(existing);
	}

	public void deleteEvent(String id) {
		repository.deleteById(id);
	}
	
	public List<EventDoc> getEventsByCreatorAndRange(String creatorId, Instant start, Instant end) {
        return repository.findByCreatorIdAndStartTimeBetween(creatorId, start, end);
    }
	
	public List<EventDoc> getEventsByTag(String tag) {
        return repository.findByTagsInAndIsPublicTrue(Collections.singletonList(tag));
    }
	
	public Page<EventDoc> getEventsByTag(String tag, Pageable pageable) {
		return repository.findByIsPublicTrueAndTagsContainingIgnoreCaseOrderByStartTimeAsc(tag, pageable);
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
