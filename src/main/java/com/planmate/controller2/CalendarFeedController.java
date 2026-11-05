package com.planmate.controller2;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.User;
import com.planmate.service.CalendarExportService;
import com.planmate.service.UserService;
import com.planmate.service.UserSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A user's PlanMate calendar as a subscription for other calendar apps. Those apps fetch the link on their own,
 * without logging in, so the secret key in it is what protects it (created and reset in Settings).
 */
@RestController
@Tag(name = "Calendar Feed Controller", description = "Calendar subscriptions for other calendar apps")
public class CalendarFeedController {

	private final UserSettingsService settings;
	private final UserService userService;
	private final CalendarExportService export;

	public CalendarFeedController(UserSettingsService settings, UserService userService, CalendarExportService export) {
		this.settings = settings;
		this.userService = userService;
		this.export = export;
	}

	@GetMapping(value = "/api/v2/calendar/{key}.ics", produces = "text/calendar")
	@Operation(summary = "Your PlanMate events as a calendar subscription (.ics), by the key from Settings")
	public ResponseEntity<String> feed(@PathVariable String key) {
		Long userId = settings.userIdForCalendarKey(key);
		User user = userId == null ? null : userService.getUserById(userId);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"planmate.ics\"")
				// Always fresh, and never kept by caches in between: the link is private
				.header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
				.body(export.feedIcs(user, Instant.now()));
	}
}
