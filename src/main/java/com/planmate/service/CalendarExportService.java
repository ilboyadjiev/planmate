package com.planmate.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.util.IcsCalendar;

/**
 * PlanMate events for other calendar apps (Apple, Google, Outlook, Samsung, ...), in the .ics format: one event to
 * add, or a user's whole PlanMate calendar as a subscription that those apps keep refreshing.
 *
 * A subscription holds the events the user hosts, has said Going or Maybe to (Maybe is marked tentative), and
 * public events they joined. Invitations they have not answered, or declined, stay out, and so do events that
 * ended more than a month ago.
 */
@Service
public class CalendarExportService {

	static final Duration PAST = Duration.ofDays(31);
	static final Duration AHEAD = Duration.ofDays(400);
	static final int MAX_EVENTS = 1000;

	private final MongoTemplate mongo;
	private final String siteUrl;

	public CalendarExportService(MongoTemplate mongo, @Value("${app.public-url:https://www.planmate.org}") String siteUrl) {
		this.mongo = mongo;
		this.siteUrl = siteUrl;
	}

	/** One event, as the viewer stands to it (userId: null for guests). */
	public String eventIcs(EventDoc event, String userId) {
		IcsCalendar.Status status = statusFor(event, null, userId);
		return IcsCalendar.write(Collections.singletonList(new IcsCalendar.Entry(event,
				status == null ? IcsCalendar.Status.CONFIRMED : status)), null, siteUrl, Instant.now());
	}

	/** The user's PlanMate calendar, for a subscription. */
	public String feedIcs(User user, Instant now) {
		String id = String.valueOf(user.getId());
		Query query = new Query(new Criteria().andOperator(
				Criteria.where("endTime").gt(now.minus(PAST)),
				Criteria.where("startTime").lt(now.plus(AHEAD)),
				new Criteria().orOperator(
						Criteria.where("creatorId").is(user.getEmail()),
						Criteria.where("attendeeIds").is(id),
						Criteria.where("maybeIds").is(id),
						Criteria.where("participantIds").is(id))))
				.with(Sort.by("startTime").ascending())
				.limit(MAX_EVENTS);
		List<IcsCalendar.Entry> entries = new ArrayList<>();
		for (EventDoc event : mongo.find(query, EventDoc.class)) {
			IcsCalendar.Status status = statusFor(event, user.getEmail(), id);
			if (status != null) {
				entries.add(new IcsCalendar.Entry(event, status));
			}
		}
		return IcsCalendar.write(entries, "PlanMate", siteUrl, now);
	}

	/** Confirmed: hosting, Going, joined. Tentative: Maybe. Null: not in the user's calendar (unanswered, declined). */
	static IcsCalendar.Status statusFor(EventDoc event, String email, String userId) {
		if (email != null && email.equals(event.getCreatorId())) {
			return IcsCalendar.Status.CONFIRMED;
		}
		if (userId != null && event.getAttendeeIds() != null && event.getAttendeeIds().contains(userId)) {
			return IcsCalendar.Status.CONFIRMED;
		}
		if (userId != null && event.getMaybeIds() != null && event.getMaybeIds().contains(userId)) {
			return IcsCalendar.Status.TENTATIVE;
		}
		String answer = userId == null || event.getResponses() == null ? null : event.getResponses().get(userId);
		if ("accepted".equals(answer)) {
			return IcsCalendar.Status.CONFIRMED;
		}
		if ("maybe".equals(answer)) {
			return IcsCalendar.Status.TENTATIVE;
		}
		return null;
	}

	/** A file name for one event: "jazz-night-at-unterfahrt.ics". */
	public static String fileName(EventDoc event) {
		String slug = event.getTitle() == null ? "" : java.text.Normalizer
				.normalize(event.getTitle(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (slug.length() > 60) {
			slug = slug.substring(0, 60).replaceAll("-$", "");
		}
		return (slug.isEmpty() ? "planmate-event" : slug) + ".ics";
	}
}
