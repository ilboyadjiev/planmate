package com.planmate.discovery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mongodb.client.result.DeleteResult;
import com.planmate.dto2.EventDoc;

/**
 * Nightly tidy-up of the events collection: imported "PlanMate Picks" events (creator {@code discovery.events-creator})
 * that ended more than {@code retentionDays} ago and that nobody joined or answered Maybe. Discover and search already
 * hide events that have ended, so these are invisible clutter; removing them keeps the collection — and the small VM's
 * Mongo — from growing without bound as new public events are imported every day.
 *
 * <p>An event anyone joined (attendeeIds) or answered Maybe to (maybeIds) is left alone: it stays in that person's
 * calendar as their history. Only events created by the Picks account are ever touched, so users' own events and the
 * events they host are never removed.
 */
@Component
public class DiscoveryEventCleanup {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryEventCleanup.class);

	private final MongoTemplate mongo;
	private final boolean enabled;
	private final String creator;
	private final int retentionDays;

	public DiscoveryEventCleanup(MongoTemplate mongo,
			@Value("${discovery.cleanup.enabled:true}") boolean enabled,
			@Value("${discovery.events-creator:events@planmate.org}") String creator,
			@Value("${discovery.cleanup.retention-days:30}") int retentionDays) {
		this.mongo = mongo;
		this.enabled = enabled;
		this.creator = creator;
		this.retentionDays = retentionDays;
	}

	/** Runs once a day (04:30 by default). Disabled with discovery.cleanup.enabled=false. */
	@Scheduled(cron = "${discovery.cleanup.cron:0 30 4 * * *}")
	public void run() {
		if (!enabled) {
			return;
		}
		long removed = prune(creator, retentionDays, Instant.now());
		if (removed > 0) {
			log.info("Removed {} past '{}' events (ended over {} days ago, nobody joined)", removed, creator, retentionDays);
		}
	}

	/**
	 * Removes events created by {@code creator} that have no attendees and no Maybe answers and whose end is more than
	 * {@code retentionDays} before {@code now}. An event with no end time stored (common for web-feed imports, where
	 * only a start was found) counts by its start time instead. Returns how many were removed.
	 */
	public long prune(String creator, int retentionDays, Instant now) {
		Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
		// Ended long ago: a real end before the cutoff, or no end and a start before it ({ endTime: null } also
		// matches a missing field in Mongo, like the feed queries in EventMongoRepository).
		Criteria endedLongAgo = new Criteria().orOperator(
				Criteria.where("endTime").lt(cutoff),
				Criteria.where("endTime").is(null).and("startTime").lt(cutoff));
		Query query = Query.query(new Criteria().andOperator(
				Criteria.where("creatorId").is(creator),
				Criteria.where("attendeeIds").size(0),
				Criteria.where("maybeIds").size(0),
				endedLongAgo));
		DeleteResult result = mongo.remove(query, EventDoc.class);
		return result.getDeletedCount();
	}
}
