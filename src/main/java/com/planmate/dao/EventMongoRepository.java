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
     * Events in the range that the user created, was invited to (participantIds) or joined from the feed
     * (attendeeIds). Both id lists hold user ids as strings.
     */
    @Query("{ 'startTime': { $gt: ?2, $lt: ?3 }, $or: [ { 'creatorId': ?0 }, { 'participantIds': ?1 }, { 'attendeeIds': ?1 } ] }")
    List<EventDoc> findCreatedOrParticipatingInRange(String creatorId, String participantId, Instant startTime, Instant endTime);
    
    /** Feed candidates: public events that have not ended yet. Order and limit come from the pageable. */
    @Query("{ 'isPublic': true, 'endTime': { $gt: ?0 } }")
    List<EventDoc> findUpcomingPublic(Instant now, Pageable pageable);

    /** Same as findUpcomingPublic, limited to one tag (tags are stored normalised: lower case, leading '#'). */
    @Query("{ 'isPublic': true, 'endTime': { $gt: ?0 }, 'tags': ?1 }")
    List<EventDoc> findUpcomingPublicWithTag(Instant now, String tag, Pageable pageable);
}
