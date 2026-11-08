package com.planmate.controller2;

import java.security.Principal;
import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.service.EventSearchService;
import com.planmate.service.FeedService;

/**
 * Event search: GET /api/v2/events/search?q=jazz+munich&page=0&size=10.
 *
 * Returns upcoming events as feed cards, best match first (see EventSearchService), each with matchedBy saying why
 * it matched. Guests can search public events; signed-in users also find their own and their invitations, and
 * events by friend or group name. Fewer than two letters returns no results.
 */
@RestController
@RequestMapping("/api/v2/events")
public class EventSearchController {

	private final EventSearchService searchService;

	public EventSearchController(EventSearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping("/search")
	public ResponseEntity<FeedService.FeedPage> search(@RequestParam(name = "q", defaultValue = "") String query,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			Principal principal) {
		return ResponseEntity.ok(searchService.search(principal == null ? null : principal.getName(), query, page, size, from, to));
	}
}
