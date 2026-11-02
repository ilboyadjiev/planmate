package com.planmate.controller2;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dto2.EventDoc;
import com.planmate.service.EventMongoService;

/**
 * Calendar events for both logged-in users and guests.
 *
 * Ownership is always decided by the server:
 * - Logged-in users own events under their email (the JWT subject). Any creatorId sent by the client is ignored.
 * - Guests (no token) identify themselves with their guest id ("guest_..."), which acts as a bearer secret.
 */
@RestController
@RequestMapping("/api/v2/events")
public class EventMongoController {

	static final Pattern GUEST_ID = Pattern.compile("^guest_[A-Za-z0-9-]{10,64}$");
	private static final int MAX_FEED_PAGE_SIZE = 50;

	private final EventMongoService eventService;

	public EventMongoController(EventMongoService eventService) {
		this.eventService = eventService;
	}

	/** The identity that owns events for this request. */
	static String resolveOwner(Principal principal, String guestId) {
		if (principal != null) {
			if (principal.getName() == null) {
				// Fail closed: never fall through to guest handling or match events with a null owner
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			}
			return principal.getName();
		}
		if (guestId != null && GUEST_ID.matcher(guestId).matches()) {
			return guestId;
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in or provide a valid guest id.");
	}

	private EventDoc findOwnedEvent(String id, String owner) {
		EventDoc existing = findEvent(id);
		if (!owner.equals(existing.getCreatorId())) {
			// 404 rather than 403, so event ids of other users cannot be probed
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
		}
		return existing;
	}

	private EventDoc findEvent(String id) {
		try {
			return eventService.getEventById(id);
		} catch (RuntimeException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
		}
	}

	@PostMapping
	public ResponseEntity<EventDoc> createEvent(@RequestBody EventDoc event, Principal principal) {
		event.setCreatorId(resolveOwner(principal, event.getCreatorId()));
		// Never let the client choose the id: save() with an existing id would overwrite someone else's event
		event.setId(null);
		return new ResponseEntity<>(eventService.createEvent(event), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EventDoc> getEventById(@PathVariable String id,
			@RequestParam(required = false) String guestId, Principal principal) {
		EventDoc event = findEvent(id);
		if (Boolean.TRUE.equals(event.getIsPublic())) {
			return ResponseEntity.ok(event);
		}
		return ResponseEntity.ok(findOwnedEvent(id, resolveOwner(principal, guestId)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<EventDoc> updateEvent(@PathVariable String id, @RequestBody EventDoc event,
			Principal principal) {
		findOwnedEvent(id, resolveOwner(principal, event.getCreatorId()));
		return ResponseEntity.ok(eventService.updateEvent(id, event));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteEvent(@PathVariable String id, @RequestParam(required = false) String guestId,
			Principal principal) {
		findOwnedEvent(id, resolveOwner(principal, guestId));
		eventService.deleteEvent(id);
		return ResponseEntity.noContent().build();
	}

	/** creatorId is only read for guests; logged-in users always get their own events. */
	@GetMapping("/range")
	public ResponseEntity<List<EventDoc>> getEventsByRange(@RequestParam(required = false) String creatorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end, Principal principal) {
		String owner = resolveOwner(principal, creatorId);
		return ResponseEntity.ok(eventService.getEventsByCreatorAndRange(owner, start, end));
	}

	@GetMapping("/feed")
	public ResponseEntity<?> getFeedEvents(@RequestParam(required = false) String tag,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "4") int size) {
		if (StringUtils.isBlank(tag)) {
			return ResponseEntity.badRequest().build();
		}
		Pageable paging = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_FEED_PAGE_SIZE),
				Sort.by("startTime").ascending());
		Page<EventDoc> pageEvents = eventService.getEventsByTag(tag, paging);

		Map<String, Object> response = new HashMap<>();
		response.put("events", pageEvents.getContent());
		response.put("currentPage", pageEvents.getNumber());
		response.put("totalPages", pageEvents.getTotalPages());
		return ResponseEntity.ok(response);
	}

	/**
	 * Moves a guest's events into the logged-in user's account. Requires authentication (see SecurityConfig);
	 * the target account is always the caller, never a request parameter.
	 */
	@PostMapping("/merge")
	public ResponseEntity<Void> mergeGuestEvents(@RequestParam String guestId, Principal principal) {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		if (!GUEST_ID.matcher(guestId).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid guest id.");
		}
		eventService.mergeGuestEvents(guestId, principal.getName());
		return ResponseEntity.ok().build();
	}
}
