package com.planmate.controller2;

import java.time.Instant;
import java.util.List;

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

import com.planmate.dto2.EventDoc;
import com.planmate.service.EventMongoService;

@RestController
@RequestMapping("/api/v2/events")
public class EventMongoController {

	private final EventMongoService eventService;

	public EventMongoController(EventMongoService eventService) {
		this.eventService = eventService;
	}

	@PostMapping
	public ResponseEntity<EventDoc> createEvent(@RequestBody EventDoc event) {
		EventDoc savedEvent = eventService.createEvent(event);
		return new ResponseEntity<>(savedEvent, HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EventDoc> getEventById(@PathVariable String id) {
		return ResponseEntity.ok(eventService.getEventById(id));
	}

	@GetMapping("/user/{creatorId}")
	public ResponseEntity<List<EventDoc>> getEventsByCreator(@PathVariable String creatorId) {
		return ResponseEntity.ok(eventService.getEventsByCreator(creatorId));
	}

	@PutMapping("/{id}")
	public ResponseEntity<EventDoc> updateEvent(@PathVariable String id, @RequestBody EventDoc event) {
		return ResponseEntity.ok(eventService.updateEvent(id, event));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
		eventService.deleteEvent(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/range")
	public ResponseEntity<List<EventDoc>> getEventsByRange(@RequestParam String creatorId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

		return ResponseEntity.ok(eventService.getEventsByCreatorAndRange(creatorId, start, end));
	}

	@GetMapping("/feed")
	public ResponseEntity<List<EventDoc>> getFeedEvents(@RequestParam String tag) {
		return ResponseEntity.ok(eventService.getEventsByTag(tag));
	}
}