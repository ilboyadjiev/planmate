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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto2.EventDoc;
import com.planmate.dto2.FeedEvent;
import com.planmate.service.CalendarExportService;
import com.planmate.service.CityTagService;
import com.planmate.service.EventMongoService;
import com.planmate.service.FeedService;
import com.planmate.service.FriendGroupService;
import com.planmate.service.FriendshipService;
import com.planmate.service.UserService;
import com.planmate.util.ConstantsUtil;
import com.planmate.util.PublicViews;

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
	static final Set<String> RESPONSES = Set.of("accepted", "maybe", "declined");

	private final EventMongoService eventService;
	private final UserService userService;
	private final FriendshipService friendshipService;
	private final FeedService feedService;
	private final FriendGroupService friendGroupService;
	private final CityTagService cityTagService;
	private final CalendarExportService calendarExport;

	public EventMongoController(EventMongoService eventService, UserService userService,
			FriendshipService friendshipService, FeedService feedService, FriendGroupService friendGroupService,
			CityTagService cityTagService, CalendarExportService calendarExport) {
		this.eventService = eventService;
		this.userService = userService;
		this.friendshipService = friendshipService;
		this.feedService = feedService;
		this.friendGroupService = friendGroupService;
		this.cityTagService = cityTagService;
		this.calendarExport = calendarExport;
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
			event.setResponses(new HashMap<>());
			event.setGroupIds(new ArrayList<>());
		}
		return event;
	}

	/**
	 * Adds the name and username of everyone invited, so the organizer and invitees can see who answered what,
	 * also for people who are not their friends (e.g. invited through a group). Never emails or contact details.
	 */
	/**
	 * An invitee's view: the organizer by name (not email), and only the invited groups the invitee is a member
	 * of. Other invitees must not even learn that a group exists.
	 */
	private EventDoc forInvitee(EventDoc event, String viewerEmail, Map<String, User> hostCache,
			Map<String, Map<String, Object>> people, Set<String> myGroupIds) {
		forNonOwner(event, ROLE_INVITED, hostCache);
		withParticipantNames(event, people);
		User creator = event.getCreatorId() == null ? null
				: hostCache.computeIfAbsent(event.getCreatorId(), userService::getUserByEmail);
		event.setOrganizer(creator == null ? null : PublicViews.person(creator));
		event.setGroupIds(event.getGroupIds() == null ? new ArrayList<>()
				: event.getGroupIds().stream().filter(myGroupIds::contains).collect(Collectors.toList()));
		return event;
	}

	private Set<String> groupIdsOf(String email) {
		return email == null ? Collections.emptySet()
				: friendGroupService.groupsOf(email).stream().map(g -> String.valueOf(g.getId())).collect(Collectors.toSet());
	}

	private EventDoc withParticipantNames(EventDoc event, Map<String, Map<String, Object>> people) {
		List<Map<String, Object>> profiles = new ArrayList<>();
		for (String id : event.getParticipantIds() == null ? Collections.<String>emptyList() : event.getParticipantIds()) {
			Map<String, Object> person = people.computeIfAbsent(id, this::publicProfile);
			if (person != null) {
				profiles.add(person);
			}
		}
		event.setParticipantProfiles(profiles);
		return event;
	}

	private Map<String, Object> publicProfile(String userId) {
		try {
			return PublicViews.person(userService.getUserById(Long.valueOf(userId)));
		} catch (NumberFormatException e) {
			return null;
		}
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
	 * Works out who an event invites. Participants must be accepted friends of the creator, or members of a friend
	 * group the creator invites (you share the group with them). Inviting a group invites all its members, so the
	 * server adds them here. Guests cannot invite anyone.
	 * Rejects the request instead of silently dropping ids, so the client never believes it invited someone.
	 * Sets participantIds and groupIds on the event.
	 */
	private void applyInvitations(EventDoc event, Principal principal) {
		Set<String> ids = cleanIds(event.getParticipantIds());
		Set<String> groupIds = cleanIds(event.getGroupIds());
		if (ids.isEmpty() && groupIds.isEmpty()) {
			event.setParticipantIds(new ArrayList<>());
			event.setGroupIds(new ArrayList<>());
			return;
		}
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guests cannot invite participants.");
		}
		String me = principal.getName();
		String myId = participantKey(principal);

		Map<String, FriendGroup> myGroups = friendGroupService.groupsOf(me).stream()
				.collect(Collectors.toMap(g -> String.valueOf(g.getId()), g -> g));
		if (!myGroups.keySet().containsAll(groupIds)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can only invite groups you are in.");
		}
		Set<String> groupMembers = groupIds.stream()
				.flatMap(id -> myGroups.get(id).getMembers().stream())
				.map(u -> String.valueOf(u.getId()))
				.filter(id -> !id.equals(myId))
				.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> allowed = friendshipService.getFriendshipList(me).stream()
				.filter(f -> ConstantsUtil.FRIENDSHIP_STATUS_ACCEPTED.equalsIgnoreCase(f.getStatus()))
				.map(f -> otherUser(f, me))
				.filter(u -> u != null && u.getId() != null)
				.map(u -> String.valueOf(u.getId()))
				.collect(Collectors.toSet());
		allowed.addAll(groupMembers);
		if (!allowed.containsAll(ids)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participants must be your friends.");
		}
		ids.addAll(groupMembers);
		event.setParticipantIds(new ArrayList<>(ids));
		event.setGroupIds(new ArrayList<>(groupIds));
	}

	private static Set<String> cleanIds(List<String> requested) {
		return requested == null ? new LinkedHashSet<>()
				: requested.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isEmpty())
						.collect(Collectors.toCollection(LinkedHashSet::new));
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
		applyInvitations(event, principal);
		// Never let the client choose the id: save() with an existing id would overwrite someone else's event
		event.setId(null);
		event.setAttendeeIds(new ArrayList<>());
		event.setResponses(new HashMap<>());
		// Only the discovery import marks events cancelled or links them to a source
		event.setCancelled(null);
		event.setSourceUrl(null);
		cityTagService.apply(event, null);
		return new ResponseEntity<>(eventService.createEvent(event), HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EventDoc> getEventById(@PathVariable String id,
			@RequestParam(required = false) String guestId, Principal principal) {
		EventDoc event = findEvent(id);
		String caller = principal != null ? principal.getName() : guestId;
		if (caller != null && caller.equals(event.getCreatorId())) {
			event.setViewerRole(ROLE_HOST);
			return ResponseEntity.ok(withParticipantNames(event, new HashMap<>()));
		}
		if (isParticipant(event, principal)) {
			return ResponseEntity.ok(forInvitee(event, principal.getName(), new HashMap<>(), new HashMap<>(), groupIdsOf(principal.getName())));
		}
		if (Boolean.TRUE.equals(event.getIsPublic())) {
			String role = contains(event.getAttendeeIds(), participantKey(principal)) ? ROLE_GOING : null;
			return ResponseEntity.ok(forNonOwner(event, role, new HashMap<>()));
		}
		return ResponseEntity.ok(findOwnedEvent(id, resolveOwner(principal, guestId)));
	}

	/**
	 * One event as a feed card, for its own page (shared links, /events/{id}). Public events are open to everyone,
	 * guests too; private ones only to their host, the people invited and those going. Others get no details, only
	 * why: 401 when not logged in (logging in may help), 403 when logged in but not invited. 404 when it is gone.
	 */
	@GetMapping("/{id}/card")
	public ResponseEntity<FeedEvent> getEventCard(@PathVariable String id, Principal principal) {
		EventDoc event = findEvent(id);
		String email = principal == null ? null : principal.getName();
		requireVisible(event, principal);
		FeedEvent card = feedService.toFeedEvent(event, email);
		card.setIsPublic(Boolean.TRUE.equals(event.getIsPublic()));
		return ResponseEntity.ok(card);
	}

	/**
	 * One event as an .ics file, to add to Apple, Google, Outlook, Samsung or any other calendar. Same rules as
	 * the event page: public events for everyone, private ones only for the people involved.
	 */
	@GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
	public ResponseEntity<String> getEventIcs(@PathVariable String id, Principal principal) {
		EventDoc event = findEvent(id);
		String myId = requireVisible(event, principal);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8))
				// inline: phones offer "Add to calendar" right away; computers download the file
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + CalendarExportService.fileName(event) + "\"")
				.body(calendarExport.eventIcs(event, myId));
	}

	/**
	 * Who may open an event's page or file: everyone for public events; the host, the invited and those going for
	 * private ones. Others learn only why not (401: log in; 403: not invited). Returns the caller's user id.
	 */
	private String requireVisible(EventDoc event, Principal principal) {
		String email = principal == null ? null : principal.getName();
		String myId = participantKey(principal);
		boolean visible = Boolean.TRUE.equals(event.getIsPublic())
				|| (email != null && email.equals(event.getCreatorId()))
				|| contains(event.getParticipantIds(), myId)
				|| contains(event.getAttendeeIds(), myId);
		if (!visible) {
			throw email == null
					? new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in to view details on this event.")
					: new ResponseStatusException(HttpStatus.FORBIDDEN,
							"The sender of this event has to invite you in order for you to see the details.");
		}
		return myId;
	}

	@PutMapping("/{id}")
	public ResponseEntity<EventDoc> updateEvent(@PathVariable String id, @RequestBody EventDoc event,
			Principal principal) {
		// Creator only: participants get 404 here just like strangers, their view is read-only
		EventDoc existing = findOwnedEvent(id, resolveOwner(principal, event.getCreatorId()));
		applyInvitations(event, principal);
		cityTagService.apply(event, existing.getCityTag());
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
		Map<String, Map<String, Object>> people = new HashMap<>();
		Set<String> myGroupIds = null; // looked up once, only if there are invitations
		List<EventDoc> events = eventService.getVisibleEventsInRange(owner, myId, start, end);
		for (EventDoc event : events) {
			if (owner.equals(event.getCreatorId())) {
				event.setViewerRole(ROLE_HOST);
				withParticipantNames(event, people);
			} else if (contains(event.getParticipantIds(), myId)) {
				if (myGroupIds == null) {
					myGroupIds = groupIdsOf(principal == null ? null : principal.getName());
				}
				forInvitee(event, principal == null ? null : principal.getName(), hostCache, people, myGroupIds);
			} else {
				forNonOwner(event, ROLE_GOING, hostCache);
			}
		}
		return ResponseEntity.ok(events);
	}

	/**
	 * Upcoming public events ranked for the caller (see FeedService). No tag means "For you": all upcoming public
	 * events. Guests get the same feed without the friend signals. With group, the feed shows that friend group's
	 * upcoming events instead (members only); with nearby=true, public events in the city from the user's profile.
	 */
	@GetMapping("/feed")
	public ResponseEntity<FeedService.FeedPage> getFeedEvents(@RequestParam(required = false) String tag,
			@RequestParam(required = false) Long group, @RequestParam(defaultValue = "false") boolean nearby,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "5") int size, Principal principal) {
		String viewer = principal == null ? null : principal.getName();
		if (group != null) {
			if (viewer == null) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			}
			return ResponseEntity.ok(feedService.getGroupFeed(viewer, group, page, size));
		}
		if (nearby) {
			if (viewer == null) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			}
			return ResponseEntity.ok(feedService.getNearbyFeed(viewer, page, size));
		}
		return ResponseEntity.ok(feedService.getFeed(viewer, tag, page, size));
	}

	/** An invitee answers an invitation: accepted, maybe or declined. Only invited participants may answer. */
	@PutMapping("/{id}/response")
	public ResponseEntity<EventDoc> respond(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal principal) {
		String myId = requireUserId(principal);
		String response = body == null ? null : body.get("response");
		if (response == null || !RESPONSES.contains(response)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer with accepted, maybe or declined.");
		}
		if (!contains(findEvent(id).getParticipantIds(), myId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
		}
		eventService.setResponse(id, myId, response);
		return ResponseEntity.ok(forInvitee(findEvent(id), principal.getName(), new HashMap<>(), new HashMap<>(),
				groupIdsOf(principal.getName())));
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
		if (Boolean.TRUE.equals(event.getCancelled())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This event was cancelled.");
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
