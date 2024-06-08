package com.planmate.controller;

import java.util.List;

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

    @Autowired
    private JwtUtil jwtUtil;

	@Autowired
	private EventService eventService;

	@GetMapping
	@Operation(summary = "Get all events, no filtering. Use with caution.")
	public ResponseEntity<List<Event>> getAllEvents() {
		return new ResponseEntity<>(eventService.getAllEvents(), HttpStatus.OK);
	}

	@GetMapping("/{user}")
	public ResponseEntity<List<Event>> getEventForUser(@PathVariable String user) {
		return new ResponseEntity<>(eventService.getAllEventsForUser(user), HttpStatus.OK);
	}
	
	@PostMapping("")
	public ResponseEntity<Event> createEvent(@RequestBody Event event, @RequestHeader("Authorization") String authorizationHeader) {
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
	public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestBody Event event) {
		return new ResponseEntity<>(eventService.updateEvent(id, event), HttpStatus.OK);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Event> deleteEvent(@PathVariable Long id) {
		Event toDelete = eventService.getEventById(id);
		boolean success = eventService.deleteEvent(toDelete);
		return new ResponseEntity<>(success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
