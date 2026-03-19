package com.planmate.controller;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.Event;
import com.planmate.service.EventService;
import com.planmate.util.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/events")
//@Api(value = "Event Management Interface", tags = "Events")
@Tag(name = "Event Controller", description = "Event Management Interface")
public class EventController {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EventController.class);

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private EventService eventService;

	@GetMapping
	@Operation(summary = "Get all events, no filtering. Use with caution.")
	public ResponseEntity<List<Event>> getAllEvents(Principal principal) {
		String currentUserEmail = principal.getName();
		return new ResponseEntity<>(eventService.getAllEventsForUser(currentUserEmail), HttpStatus.OK);
	}

	@GetMapping("/username/{user}")
	public ResponseEntity<List<Event>> getEventForUsername(@PathVariable String user) {
		return new ResponseEntity<>(eventService.getAllEventsForUsername(user), HttpStatus.OK);
	}

	@PostMapping("")
	public ResponseEntity<Event> createEvent(@RequestBody Event event,
			@RequestHeader("Authorization") String authorizationHeader) {
		// Retrieve the currently logged in user
		String username = "";
		if (authorizationHeader != null) {
			String jwtToken = authorizationHeader.replace("Bearer ", "");
			username = jwtUtil.extractUsername(jwtToken);
		} else {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		return new ResponseEntity<>(eventService.createNewEvent(event, username), HttpStatus.OK);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestBody Event event, Principal principal) {
		logger.info("Updating event with ID: " + id);

		Event existingEvent = eventService.getEventById(id);

		if (existingEvent == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// only allow the event creator or participants to update the event
		String currentEditor = principal.getName();
		String originalEventCreator = existingEvent.getUser().getEmail();

		boolean isCreator = StringUtils.equalsIgnoreCase(originalEventCreator, currentEditor);
		boolean currentEditorIsInvited = existingEvent.getParticipants() != null && existingEvent.getParticipants()
				.stream().anyMatch(p -> StringUtils.equalsIgnoreCase(p.getEmail(), currentEditor));

		if (!isCreator && !currentEditorIsInvited) {
			logger.warn("User " + currentEditor + " is NOT authorized to update event with ID: " + id);
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		return new ResponseEntity<>(eventService.updateEvent(id, existingEvent, event), HttpStatus.OK);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteEvent(@PathVariable Long id, Principal principal) {
		Event existingEvent = eventService.getEventById(id);

		if (existingEvent == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (!existingEvent.getUser().getEmail().equals(principal.getName())) {
			return new ResponseEntity<>("You do not have permission to delete this event.", HttpStatus.FORBIDDEN);
		}

		eventService.deleteEvent(existingEvent);
		return new ResponseEntity<>("Event deleted", HttpStatus.OK);
	}

	@GetMapping("/range")
	public ResponseEntity<?> getEventsInRange(@RequestParam("start") String startStr,
			@RequestParam("end") String endStr, Principal principal) {

		try {
			Timestamp startTime = Timestamp.valueOf(startStr);
			Timestamp endTime = Timestamp.valueOf(endStr);

			String currentUserEmail = principal.getName();
			List<Event> events = eventService.findByUserEmailAndStartTimeBetween(currentUserEmail, startTime, endTime);

			return new ResponseEntity<>(events, HttpStatus.OK);

		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>("Invalid date format. Please use 'yyyy-MM-dd HH:mm:ss'",
					HttpStatus.BAD_REQUEST);
		}
	}

}
