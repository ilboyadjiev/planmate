package com.planmate.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

	/** Commit SHA of the running build (set via APP_VERSION in the Docker image). Lets deploys verify what is live. */
	@Value("${app.version:unknown}")
	private String version;

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> healthCheck() {
		Map<String, Object> response = new HashMap<>();
		response.put("status", "UP");
		response.put("service", "planmate-backend");
		response.put("version", version);
		response.put("timestamp", LocalDateTime.now());

		return ResponseEntity.ok(response);
	}
}