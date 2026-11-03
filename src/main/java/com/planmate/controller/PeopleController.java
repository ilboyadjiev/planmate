package com.planmate.controller;

import java.security.Principal;
import java.util.Map;

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
}
