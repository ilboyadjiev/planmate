package com.planmate.dao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.planmate.dto2.EventDoc;

@Repository
public interface EventMongoRepository extends MongoRepository<EventDoc, String> {
    
    List<EventDoc> findByCreatorId(String creatorId);
    
    List<EventDoc> findByCreatorIdAndStartTimeBetween(String creatorId, Instant startTime, Instant endTime);

    /**
     * Events in the range that the user created, was invited to (participantIds), joined from the feed
     * (attendeeIds) or answered Maybe to (maybeIds). The id lists hold user ids as strings.
     */
    @Query("{ 'startTime': { $gt: ?2, $lt: ?3 }, $or: [ { 'creatorId': ?0 }, { 'participantIds': ?1 }, { 'attendeeIds': ?1 }, { 'maybeIds': ?1 } ] }")
    List<EventDoc> findCreatedOrParticipatingInRange(String creatorId, String participantId, Instant startTime, Instant endTime);
    
    /** Feed candidates: public events that have not ended yet. Order and limit come from the pageable. */
    @Query("{ 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?0 } }")
    List<EventDoc> findUpcomingPublic(Instant now, Pageable pageable);

    /**
     * A friend group's events that have not ended yet and that the user created or was invited to, for the
     * group's feed. Public or not: group members are invited, so they may see them.
     */
    @Query("{ 'groupIds': ?0, 'endTime': { $gt: ?1 }, $or: [ { 'creatorId': ?2 }, { 'participantIds': ?3 } ] }")
    org.springframework.data.domain.Page<EventDoc> findUpcomingForGroupMember(String groupId, Instant now,
            String email, String userId, Pageable pageable);

    /**
     * "Around me": upcoming public events in a city. An event matches when its structured location city equals the
     * city (cityPattern), its free-text location name mentions the city as a word (namePattern), or it carries the
     * city as a tag (cityTag, e.g. "#munich"). Both patterns are case-insensitive regular expressions.
     */
    @Query("{ 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?0 }, $or: [ { 'location.city': { $regex: ?1, $options: 'i' } }, "
            + "{ 'location.name': { $regex: ?2, $options: 'i' } }, { 'tags': ?3 } ] }")
    List<EventDoc> findUpcomingPublicInCity(Instant now, String cityPattern, String namePattern, String cityTag,
            Pageable pageable);

    /** Profile pages: upcoming public events someone hosts. */
    @Query("{ 'creatorId': ?0, 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?1 } }")
    List<EventDoc> findUpcomingPublicHostedBy(String creatorEmail, Instant now, Pageable pageable);

    /** Profile pages: upcoming public events someone joined from the feed. */
    @Query("{ 'attendeeIds': ?0, 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?1 } }")
    List<EventDoc> findUpcomingPublicAttendedBy(String userId, Instant now, Pageable pageable);

    /** Same as findUpcomingPublic, limited to one tag (tags are stored normalised: lower case, leading '#'). */
    /** Upcoming public dates of a show: same host and title (FeedService narrows them down to the same venue). */
    @Query("{ 'creatorId': ?0, 'title': ?1, 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?2 } }")
    List<EventDoc> findUpcomingPublicByCreatorAndTitle(String creatorId, String title, Instant now, Pageable pageable);

    @Query("{ 'isPublic': true, 'cancelled': { $ne: true }, 'endTime': { $gt: ?0 }, 'tags': ?1 }")
    List<EventDoc> findUpcomingPublicWithTag(Instant now, String tag, Pageable pageable);
}
