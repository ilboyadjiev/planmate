package com.planmate.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;

@Service
public class EventMongoService {

	private final EventMongoRepository repository;
	private final MongoTemplate mongoTemplate;

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
		// Answers are kept for people who are still invited; uninvited people's answers go with them
		if (existing.getResponses() != null) {
			existing.getResponses().keySet().retainAll(
					updatedData.getParticipantIds() == null ? List.of() : updatedData.getParticipantIds());
		}
		// attendeeIds and responses are otherwise left alone: only join/leave and respond change them
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

	/** Records an invitee's answer. userId is a numeric user id, so it is safe as a field name. */
	public void setResponse(String eventId, String userId, String response) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)),
				new Update().set("responses." + userId, response), EventDoc.class);
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
}
