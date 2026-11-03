package com.planmate.controller2;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.service.GamePlanService;
import com.planmate.util.AiSummaryResponse;
import com.planmate.util.AiSummaryResult;

@RestController
@RequestMapping("/api/v2/ai")
public class AiController {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AiController.class);

	private final GamePlanService gamePlanService;

	public AiController(GamePlanService gamePlanService) {
		this.gamePlanService = gamePlanService;
	}

	private static class SummaryTracker {
		String cachedSummary;
		Instant lastGeneratedAt;
		Instant nextAvailableRefresh;
		int dailyCalls;

		public SummaryTracker(String cachedSummary, Instant lastGeneratedAt, Instant nextAvailableRefresh, int initialCalls) {
			this.cachedSummary = cachedSummary;
			this.lastGeneratedAt = lastGeneratedAt;
			this.nextAvailableRefresh = nextAvailableRefresh;
			this.dailyCalls = initialCalls;
		}
	}

	private final Map<String, SummaryTracker> userSummaryCache = new ConcurrentHashMap<>();
	private static final int MAX_DAILY_CALLS = 10;
	private static final int COOLDOWN_MINUTES = 5;

	@GetMapping("/summary")
	public ResponseEntity<?> getAiSummary(Principal principal,
			@RequestParam(required = false, defaultValue = "false") boolean force) {

		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User is not authenticated."));
		}

		String username = principal.getName();
		Instant now = Instant.now();
		SummaryTracker tracker = userSummaryCache.get(username);

		if (tracker != null && tracker.lastGeneratedAt.plus(24, ChronoUnit.HOURS).isBefore(now)) {
			tracker = null;
		}

		// Check if we should return the cached version instead of hitting Gemini
		boolean enforceCooldown = tracker != null && now.isBefore(tracker.nextAvailableRefresh);
		boolean hitDailyLimit = tracker != null && tracker.dailyCalls >= MAX_DAILY_CALLS;

		if (tracker != null && (!force || enforceCooldown || hitDailyLimit)) {
			Instant nextRefresh = hitDailyLimit ? tracker.lastGeneratedAt.plus(24, ChronoUnit.HOURS)
					: tracker.nextAvailableRefresh;

			AiSummaryResponse response = new AiSummaryResponse(tracker.cachedSummary, tracker.lastGeneratedAt,
					nextRefresh, MAX_DAILY_CALLS - tracker.dailyCalls);
			return ResponseEntity.ok(response);
		}

		// If checks pass, generate a new summary from Gemini
		AiSummaryResult summaryResult = gamePlanService.getGamePlan(username);
		Instant nextRefreshTime = now.plus(COOLDOWN_MINUTES, ChronoUnit.MINUTES);
		int callsToIncrement = summaryResult.isApiCalled() ? 1 : 0;

		if (tracker == null) {
			tracker = new SummaryTracker(summaryResult.getText(), now, nextRefreshTime, callsToIncrement);
			userSummaryCache.put(username, tracker);
		} else {
			tracker.cachedSummary = summaryResult.getText();
			tracker.lastGeneratedAt = now;
			tracker.nextAvailableRefresh = nextRefreshTime;
			tracker.dailyCalls += callsToIncrement;
		}

		logger.info("Game plan for user {} was generated successfully. Calls remaining today: {}", username,
				MAX_DAILY_CALLS - tracker.dailyCalls);

		AiSummaryResponse response = new AiSummaryResponse(tracker.cachedSummary, tracker.lastGeneratedAt,
				tracker.nextAvailableRefresh, MAX_DAILY_CALLS - tracker.dailyCalls);
		return ResponseEntity.ok(response);
	}

}
