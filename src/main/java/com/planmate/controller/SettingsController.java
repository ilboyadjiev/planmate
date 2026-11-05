package com.planmate.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.User;
import com.planmate.service.AccountExportService;
import com.planmate.service.UserService;
import com.planmate.service.UserSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The caller's own settings (Settings page) and "Download my data". */
@RestController
@Tag(name = "Settings Controller", description = "Your settings and your data")
public class SettingsController {

	private final UserService userService;
	private final UserSettingsService settings;
	private final AccountExportService export;

	public SettingsController(UserService userService, UserSettingsService settings, AccountExportService export) {
		this.userService = userService;
		this.settings = settings;
		this.export = export;
	}

	@GetMapping("/api/v1/settings")
	@Operation(summary = "Your settings, with defaults for those you never changed")
	public ResponseEntity<Map<String, Object>> get(Principal principal) {
		User me = caller(principal);
		return me == null ? new ResponseEntity<>(HttpStatus.UNAUTHORIZED) : ResponseEntity.ok(settings.view(me));
	}

	@PatchMapping("/api/v1/settings")
	@Operation(summary = "Change some settings; the others stay as they are. Returns all settings.")
	public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, Object> changes, Principal principal) {
		User me = caller(principal);
		return me == null ? new ResponseEntity<>(HttpStatus.UNAUTHORIZED)
				: ResponseEntity.ok(settings.update(me.getId(), changes));
	}

	@PostMapping("/api/v1/settings/calendar-feed")
	@Operation(summary = "Create your calendar subscription link, or a new one with reset=true (the old one stops working)")
	public ResponseEntity<Map<String, String>> createCalendarFeed(
			@RequestParam(required = false, defaultValue = "false") boolean reset, Principal principal) {
		User me = caller(principal);
		return me == null ? new ResponseEntity<>(HttpStatus.UNAUTHORIZED)
				: ResponseEntity.ok(Map.of("calendarFeedKey", settings.calendarFeedKey(me.getId(), reset)));
	}

	@DeleteMapping("/api/v1/settings/calendar-feed")
	@Operation(summary = "Turn the calendar subscription off: the link stops working")
	public ResponseEntity<Void> removeCalendarFeed(Principal principal) {
		User me = caller(principal);
		if (me == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		settings.removeCalendarFeed(me.getId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping(value = "/api/v1/account/export", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Everything PlanMate stores about you, as a JSON file")
	public ResponseEntity<Map<String, Object>> exportData(Principal principal) {
		User me = caller(principal);
		if (me == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		String fileName = "planmate-data-" + LocalDate.now() + ".json";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(export.export(me.getEmail()));
	}

	private User caller(Principal principal) {
		return principal == null || principal.getName() == null ? null : userService.getUserByEmail(principal.getName());
	}
}
