package com.planmate.dao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.planmate.dto2.EventDoc;

@Repository
public interface EventMongoRepository extends MongoRepository<EventDoc, String> {
    
    List<EventDoc> findByCreatorId(String creatorId);
    
    List<EventDoc> findByCreatorIdAndStartTimeBetween(String creatorId, Instant startTime, Instant endTime);
    
    List<EventDoc> findByTagsInAndIsPublicTrue(List<String> tags);
    
    Page<EventDoc> findByIsPublicTrueOrderByStartTimeAsc(Pageable pageable);
    
    Page<EventDoc> findByIsPublicTrueAndTagsContainingIgnoreCaseOrderByStartTimeAsc(String tag, Pageable pageable);
}
