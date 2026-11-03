package com.planmate.controller2;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.service.EventMongoService;
import com.planmate.service.FeedService;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;
import com.planmate.util.ConstantsUtil;

/**
 * Calendar events for both logged-in users and guests.
 *
 * Ownership is always decided by the server:
 * - Logged-in users own events under their email (the JWT subject). Any creatorId sent by the client is ignored.
 * - Guests (no token) identify themselves with their guest id ("guest_..."), which acts as a bearer secret.
 *
 * Only the creator may change or delete an event. Participants (participantIds = user ids, always accepted friends
 * of the creator) see the event in their calendar and may open it, read-only. So do attendees (attendeeIds = user
 * ids of people who joined a public event from the Discover feed); they are strangers to the creator, so they never
 * get the creator's email or the other people's ids.
 */
@RestController
@RequestMapping("/api/v2/events")
public class EventMongoController {

	static final Pattern GUEST_ID = Pattern.compile("^guest_[A-Za-z0-9-]{10,64}$");
	static final String ROLE_HOST = "host";
	static final String ROLE_INVITED = "invited";
	static final String ROLE_GOING = "going";

	private final EventMongoService eventService;
	private final UserService userService;
	private final FriendshipService friendshipService;
	private final FeedService feedService;

	public EventMongoController(EventMongoService eventService, UserService userService,
			FriendshipService friendshipService, FeedService feedService) {
		this.eventService = eventService;
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.feedService = feedService;
	}

	/** The logged-in caller's user id; 401 for guests. */
	private String requireUserId(Principal principal) {
		String key = participantKey(principal);
		if (key == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return key;
	}

	/**
	 * Prepares an event the caller does not own for display. Invited participants are friends of the creator and
	 * keep the creator and participant list; everyone else only gets the host's display name.
	 */
	private EventDoc forNonOwner(EventDoc event, String role, Map<String, User> hostCache) {
		event.setViewerRole(role);
		event.setHostName(feedService.hostName(event.getCreatorId(), hostCache));
		event.setAttendeeIds(new ArrayList<>());
		if (!ROLE_INVITED.equals(role)) {
			event.setCreatorId(null);
			event.setParticipantIds(new ArrayList<>());
		}
		return event;
	}

	private static boolean contains(List<String> ids, String id) {
		return id != null && ids != null && ids.contains(id);
	}

	/** The caller's user id as stored in participantIds, or null for guests. */
	private String participantKey(Principal principal) {
		if (principal == null || principal.getName() == null) {
			return null;
		}
		User user = userService.getUserByEmail(principal.getName());
		return user == null || user.getId() == null ? null : String.valueOf(user.getId());
	}

	private boolean isParticipant(EventDoc event, Principal principal) {
		String key = participantKey(principal);
		return key != null && event.getParticipantIds() != null && event.getParticipantIds().contains(key);
	}

	/**
	 * Participants must be accepted friends of the creator; guests cannot invite anyone.
	 * Rejects the request instead of silently dropping ids, so the client never believes it invited someone.
	 */
	private List<String> validatedParticipants(List<String> requested, Principal principal) {
		Set<String> ids = requested == null ? Collections.emptySet()
				: requested.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isEmpty())
						.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.isEmpty()) {
			return new ArrayList<>();
		}
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guests cannot invite participants.");
		}
		String me = principal.getName();
		Set<String> friendIds = friendshipService.getFriendshipList(me).stream()
				.filter(f -> ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(f.getStatus()))
				.map(f -> otherUser(f, me))
				.filter(u -> u != null && u.getId() != null)
				.map(u -> String.valueOf(u.getId()))
				.collect(Collectors.toSet());
		if (!friendIds.containsAll(ids)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participants must be your friends.");
		}
		return new ArrayList<>(ids);
	}

	private static User otherUser(Friendship friendship, String myEmail) {
		User a = friendship.getUserA();
		User b = friendship.getUserB();
		return a != null && myEmail.equals(a.getEmail()) ? b : a;
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
		event.setParticipantIds(validatedParticipants(event.getParticipantIds(), principal));
		// Never let the client choose the id: save() with an existing id would overwrite someone else's event
		event.setId(null);
		event.setAttendeeIds(new ArrayList<>());
		return new ResponseEntity<>(eventService.createEvent(event), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EventDoc> getEventById(@PathVariable String id,
			@RequestParam(required = false) String guestId, Principal principal) {
		EventDoc event = findEvent(id);
		String caller = principal != null ? principal.getName() : guestId;
		if (caller != null && caller.equals(event.getCreatorId())) {
			event.setViewerRole(ROLE_HOST);
			return ResponseEntity.ok(event);
		}
		if (isParticipant(event, principal)) {
			return ResponseEntity.ok(forNonOwner(event, ROLE_INVITED, new HashMap<>()));
		}
		if (Boolean.TRUE.equals(event.getIsPublic())) {
			String role = contains(event.getAttendeeIds(), participantKey(principal)) ? ROLE_GOING : null;
			return ResponseEntity.ok(forNonOwner(event, role, new HashMap<>()));
		}
		return ResponseEntity.ok(findOwnedEvent(id, resolveOwner(principal, guestId)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<EventDoc> updateEvent(@PathVariable String id, @RequestBody EventDoc event,
			Principal principal) {
		// Creator only: participants get 404 here just like strangers, their view is read-only
		findOwnedEvent(id, resolveOwner(principal, event.getCreatorId()));
		event.setParticipantIds(validatedParticipants(event.getParticipantIds(), principal));
		return ResponseEntity.ok(eventService.updateEvent(id, event));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteEvent(@PathVariable String id, @RequestParam(required = false) String guestId,
			Principal principal) {
		findOwnedEvent(id, resolveOwner(principal, guestId));
		eventService.deleteEvent(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Events the caller created plus events they were invited to. creatorId is only read for guests;
	 * logged-in users are identified by their token.
	 */
	@GetMapping("/range")
	public ResponseEntity<List<EventDoc>> getEventsByRange(@RequestParam(required = false) String creatorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end, Principal principal) {
		String owner = resolveOwner(principal, creatorId);
		String myId = participantKey(principal);
		Map<String, User> hostCache = new HashMap<>();
		List<EventDoc> events = eventService.getVisibleEventsInRange(owner, myId, start, end);
		for (EventDoc event : events) {
			if (owner.equals(event.getCreatorId())) {
				event.setViewerRole(ROLE_HOST);
			} else {
				forNonOwner(event, contains(event.getParticipantIds(), myId) ? ROLE_INVITED : ROLE_GOING, hostCache);
			}
		}
		return ResponseEntity.ok(events);
	}

	/**
	 * Upcoming public events ranked for the caller (see FeedService). No tag means "For you": all upcoming public
	 * events. Guests get the same feed without the friend signals.
	 */
	@GetMapping("/feed")
	public ResponseEntity<FeedService.FeedPage> getFeedEvents(@RequestParam(required = false) String tag,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
			Principal principal) {
		String viewer = principal == null ? null : principal.getName();
		return ResponseEntity.ok(feedService.getFeed(viewer, tag, page, size));
	}

	/** Join a public event from the feed. It then shows in the caller's calendar, read-only. */
	@PostMapping("/{id}/attendance")
	public ResponseEntity<FeedEvent> attend(@PathVariable String id, Principal principal) {
		String myId = requireUserId(principal);
		EventDoc event = findEvent(id);
		if (!Boolean.TRUE.equals(event.getIsPublic())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
		}
		if (principal.getName().equals(event.getCreatorId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You're hosting this event.");
		}
		if (event.getEndTime() != null && !event.getEndTime().isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This event is already over.");
		}
		eventService.addAttendee(id, myId);
		return ResponseEntity.ok(feedService.toFeedEvent(findEvent(id), principal.getName()));
	}

	/** Leave an event joined from the feed. Allowed even if the event has since been made private. */
	@DeleteMapping("/{id}/attendance")
	public ResponseEntity<FeedEvent> leave(@PathVariable String id, Principal principal) {
		String myId = requireUserId(principal);
		findEvent(id);
		eventService.removeAttendee(id, myId);
		return ResponseEntity.ok(feedService.toFeedEvent(findEvent(id), principal.getName()));
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
