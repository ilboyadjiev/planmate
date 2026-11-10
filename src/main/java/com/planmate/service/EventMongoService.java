package com.planmate.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.domain.Sort;
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
		existing.setCityTag(updatedData.getCityTag());
		existing.setLocation(updatedData.getLocation());
		existing.setParticipantIds(updatedData.getParticipantIds());
		existing.setGroupIds(updatedData.getGroupIds());
		// Answers are kept for people who are still invited; uninvited people's answers go with them
		if (existing.getResponses() != null) {
			existing.getResponses().keySet().retainAll(
					updatedData.getParticipantIds() == null ? List.of() : updatedData.getParticipantIds());
		}
		// attendeeIds, maybeIds and responses are otherwise left alone: only answering changes them
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
		setAttendance(eventId, userId, "accepted");
	}

	/**
	 * Someone not invited answers a public event: accepted (going), maybe, or declined (neither, so it leaves their
	 * calendar). Atomic; a person is in at most one of attendeeIds and maybeIds.
	 */
	public void setAttendance(String eventId, String userId, String response) {
		Update update = new Update();
		if ("accepted".equals(response)) {
			update.addToSet("attendeeIds", userId).pull("maybeIds", userId);
		} else if ("maybe".equals(response)) {
			update.addToSet("maybeIds", userId).pull("attendeeIds", userId);
		} else {
			update.pull("attendeeIds", userId).pull("maybeIds", userId);
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)), update, EventDoc.class);
	}

	/**
	 * Invitations the user has not answered yet, for the navbar's badge: upcoming, not cancelled, hosted by
	 * someone else. userId is a numeric user id, so it is safe in a field name.
	 */
	/**
	 * Upcoming events the user was invited to but has neither answered nor dismissed. dismissedBy lets a user clear
	 * an invitation from their invitations list while the event stays in their calendar (still unanswered).
	 */
	private static Query unansweredInvitations(String userId, String email, Instant now) {
		return Query.query(Criteria.where("participantIds").is(userId)
				.and("creatorId").ne(email)
				.and("cancelled").ne(true)
				.and("endTime").gt(now)
				.and("responses." + userId).exists(false)
				.and("dismissedBy").ne(userId));
	}

	public long countUnansweredInvitations(String userId, String email, Instant now) {
		return mongoTemplate.count(unansweredInvitations(userId, email, now), EventDoc.class);
	}

	/** The same invitations as the count, as events, soonest first, for the invitations list. */
	public List<EventDoc> listUnansweredInvitations(String userId, String email, Instant now) {
		return mongoTemplate.find(unansweredInvitations(userId, email, now)
				.with(Sort.by(Sort.Direction.ASC, "startTime")), EventDoc.class);
	}

	/** Dismiss an invitation from the invitations list without answering it (the event stays in the calendar). */
	public void dismissInvitation(String eventId, String userId) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)),
				new Update().addToSet("dismissedBy", userId), EventDoc.class);
	}

	/** Records an invitee's answer. userId is a numeric user id, so it is safe as a field name. */
	public void setResponse(String eventId, String userId, String response) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(eventId)),
				new Update().set("responses." + userId, response), EventDoc.class);
	}

	public void removeAttendee(String eventId, String userId) {
		setAttendance(eventId, userId, "declined");
	}

	/**
	 * A user joined a friend group: invite them to that group's events that have not started yet. Past and ongoing
	 * events are left alone, and so are events the user created themselves.
	 */
	public void inviteToUpcomingGroupEvents(Long groupId, String userId, String userEmail, Instant now) {
		Query upcoming = Query.query(Criteria.where("groupIds").is(String.valueOf(groupId))
				.and("startTime").gt(now)
				.and("creatorId").ne(userEmail));
		mongoTemplate.updateMulti(upcoming, new Update().addToSet("participantIds", userId), EventDoc.class);
	}

	/**
	 * Account deletion: deletes the events the user created and removes them from everyone else's events
	 * (invitations, answers and feed sign-ups).
	 */
	public void removeUserData(String email, String userId) {
		mongoTemplate.remove(Query.query(Criteria.where("creatorId").is(email)), EventDoc.class);
		Query involved = Query.query(new Criteria().orOperator(Criteria.where("participantIds").is(userId),
				Criteria.where("attendeeIds").is(userId), Criteria.where("maybeIds").is(userId),
				Criteria.where("dismissedBy").is(userId), Criteria.where("responses." + userId).exists(true)));
		mongoTemplate.updateMulti(involved, new Update().pull("participantIds", userId).pull("attendeeIds", userId)
				.pull("maybeIds", userId).pull("dismissedBy", userId).unset("responses." + userId), EventDoc.class);
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
