package com.planmate.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;

/**
 * When two people are both free, for planning something together.
 *
 * Someone is busy during their own events, invitations they have not declined and public events they joined.
 * Only "free" or "busy" ever leaves this class: never what the event is.
 *
 * Suggestions are made per day in three parts of the day (local time of the planner): morning 09-12,
 * afternoon 12-17 and evening 17-22, on the hour or half hour. Events must end by 22:00.
 */
@Service
public class AvailabilityService {

	static final int MAX_DAYS = 14;
	static final LocalTime DAY_END = LocalTime.of(22, 0);
	static final Object[][] PARTS = { { "Morning", LocalTime.of(9, 0), LocalTime.of(12, 0) },
			{ "Afternoon", LocalTime.of(12, 0), LocalTime.of(17, 0) },
			{ "Evening", LocalTime.of(17, 0), LocalTime.of(22, 0) } };

	private final EventMongoRepository repository;

	public AvailabilityService(EventMongoRepository repository) {
		this.repository = repository;
	}

	/** A time range someone is busy. */
	static final class Busy {
		final Instant start;
		final Instant end;

		Busy(Instant start, Instant end) {
			this.start = start;
			this.end = end;
		}

		boolean overlaps(Instant from, Instant to) {
			return start.isBefore(to) && end.isAfter(from);
		}
	}

	/** Whether the person has nothing else on between start and end. */
	public boolean isFree(User user, Instant start, Instant end) {
		return busyTimes(user, start, end).stream().noneMatch(b -> b.overlaps(start, end));
	}

	/**
	 * Shared free times, day by day: { "days": [ { "date": "2026-11-04", "slots": [ { "part": "Evening",
	 * "start": ..., "end": ... } ] } ] }. Days without a shared free time are listed with no slots.
	 */
	public Map<String, Object> sharedFreeSlots(User me, User them, LocalDate fromDay, int days, int minutes,
			ZoneId zone, Instant now) {
		int dayCount = Math.min(Math.max(1, days), MAX_DAYS);
		Duration length = Duration.ofMinutes(Math.min(Math.max(15, minutes), 12 * 60));
		Instant windowStart = fromDay.atStartOfDay(zone).toInstant();
		Instant windowEnd = fromDay.plusDays(dayCount).atStartOfDay(zone).toInstant();

		List<Busy> busy = new ArrayList<>(busyTimes(me, windowStart, windowEnd));
		busy.addAll(busyTimes(them, windowStart, windowEnd));

		List<Map<String, Object>> result = new ArrayList<>();
		for (int d = 0; d < dayCount; d++) {
			LocalDate day = fromDay.plusDays(d);
			List<Map<String, Object>> slots = new ArrayList<>();
			for (Object[] part : PARTS) {
				Instant slot = firstFreeStart(day, (LocalTime) part[1], (LocalTime) part[2], length, zone, busy, now);
				if (slot != null) {
					Map<String, Object> view = new LinkedHashMap<>();
					view.put("part", part[0]);
					view.put("start", slot);
					view.put("end", slot.plus(length));
					slots.add(view);
				}
			}
			Map<String, Object> dayView = new LinkedHashMap<>();
			dayView.put("date", day.toString());
			dayView.put("slots", slots);
			result.add(dayView);
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("days", result);
		return response;
	}

	/** The first half-hour start in [partStart, partEnd) that is in the future, ends by 22:00 and is free. */
	private static Instant firstFreeStart(LocalDate day, LocalTime partStart, LocalTime partEnd, Duration length,
			ZoneId zone, List<Busy> busy, Instant now) {
		ZonedDateTime latestEnd = day.atTime(DAY_END).atZone(zone);
		for (ZonedDateTime start = day.atTime(partStart).atZone(zone); start.toLocalTime().isBefore(partEnd)
				&& start.toLocalDate().equals(day); start = start.plusMinutes(30)) {
			ZonedDateTime end = start.plus(length);
			if (end.isAfter(latestEnd)) {
				break;
			}
			Instant from = start.toInstant();
			Instant to = end.toInstant();
			if (from.isAfter(now) && busy.stream().noneMatch(b -> b.overlaps(from, to))) {
				return from;
			}
		}
		return null;
	}

	List<Busy> busyTimes(User user, Instant from, Instant to) {
		String userId = String.valueOf(user.getId());
		// Events starting up to a day earlier can still run into the window
		List<EventDoc> events = repository.findCreatedOrParticipatingInRange(user.getEmail(), userId,
				from.minus(1, ChronoUnit.DAYS), to);
		List<Busy> busy = new ArrayList<>();
		for (EventDoc e : events) {
			if (e.getStartTime() == null || e.getEndTime() == null) {
				continue;
			}
			boolean ownEvent = user.getEmail().equals(e.getCreatorId());
			boolean declined = !ownEvent && e.getResponses() != null && "declined".equals(e.getResponses().get(userId));
			boolean joined = e.getAttendeeIds() != null && e.getAttendeeIds().contains(userId);
			if (declined && !joined) {
				continue; // a declined invitation does not keep anyone busy
			}
			Busy b = new Busy(e.getStartTime(), e.getEndTime());
			if (b.overlaps(from, to)) {
				busy.add(b);
			}
		}
		return busy;
	}
}
