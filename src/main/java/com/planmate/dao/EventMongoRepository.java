package com.planmate.dao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.planmate.dto2.EventDoc;

@Repository
public interface EventMongoRepository extends MongoRepository<EventDoc, String> {
    
    List<EventDoc> findByCreatorId(String creatorId);
    
    List<EventDoc> findByCreatorIdAndStartTimeBetween(String creatorId, Instant startTime, Instant endTime);

    /** Events in the range that the user created or is a participant of (participantIds holds user ids). */
    @Query("{ 'startTime': { $gt: ?2, $lt: ?3 }, $or: [ { 'creatorId': ?0 }, { 'participantIds': ?1 } ] }")
    List<EventDoc> findCreatedOrParticipatingInRange(String creatorId, String participantId, Instant startTime, Instant endTime);
    
    List<EventDoc> findByTagsInAndIsPublicTrue(List<String> tags);
    
    Page<EventDoc> findByIsPublicTrueOrderByStartTimeAsc(Pageable pageable);
    
    Page<EventDoc> findByIsPublicTrueAndTagsContainingIgnoreCaseOrderByStartTimeAsc(String tag, Pageable pageable);
}
