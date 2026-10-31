package com.planmate.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;

@Service
public class EventMongoService {

	private final EventMongoRepository repository;

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

	
	public void mergeGuestEvents(String guestId, String realUserId) {
		List<EventDoc> guestEvents = repository.findByCreatorId(guestId);
		if (guestEvents.isEmpty()) {
			return;
		}
		guestEvents.forEach(event -> event.setCreatorId(realUserId));
		repository.saveAll(guestEvents);
	}
}
