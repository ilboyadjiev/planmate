package com.planmate.controller;

import java.security.Principal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.service.PeopleService;

/** Profile pages of other users (see PeopleService for what they show). Logged-in users only. */
@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {

	private final PeopleService peopleService;

	public PeopleController(PeopleService peopleService) {
		this.peopleService = peopleService;
	}

	@GetMapping("/{id}")
	public Map<String, Object> profile(@PathVariable Long id, Principal principal) {
		return peopleService.profile(principal == null ? null : principal.getName(), id);
	}

	/**
	 * Times you and a friend are both free, day by day, for planning something together. Free or busy only, never
	 * what anyone is doing. from: first day (yyyy-MM-dd, default today), tz: the planner's time zone.
	 */
	@GetMapping("/{id}/free-slots")
	public Map<String, Object> freeSlots(@PathVariable Long id, @RequestParam(required = false) String from,
			@RequestParam(defaultValue = "7") int days, @RequestParam(defaultValue = "60") int minutes,
			@RequestParam(defaultValue = "Europe/Berlin") String tz, Principal principal) {
		return peopleService.sharedFreeSlots(principal == null ? null : principal.getName(), id,
				from == null ? null : LocalDate.parse(from), days, minutes, zone(tz));
	}

	/** Whether you and a friend are free at a given time. */
	@GetMapping("/{id}/availability")
	public Map<String, Object> availability(@PathVariable Long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end, Principal principal) {
		return peopleService.availabilityAt(principal == null ? null : principal.getName(), id, start, end);
	}

	private static ZoneId zone(String tz) {
		try {
			return ZoneId.of(tz);
		} catch (DateTimeException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone.");
		}
	}
}
