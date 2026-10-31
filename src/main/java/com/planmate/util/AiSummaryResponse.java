package com.planmate.util;

import java.time.Instant;

public class AiSummaryResponse {

	private String summary;
	private Instant creationDate;
	private Instant nextAvailableRefresh;
	private int remainingDailyCalls;

	public AiSummaryResponse(String summary, Instant creationDate, Instant nextAvailableRefresh,
			int remainingDailyCalls) {
		this.summary = summary;
		this.creationDate = creationDate;
		this.nextAvailableRefresh = nextAvailableRefresh;
		this.remainingDailyCalls = remainingDailyCalls;
	}

	public String getSummary() {
		return summary;
	}

	public Instant getCreationDate() {
		return creationDate;
	}

	public Instant getNextAvailableRefresh() {
		return nextAvailableRefresh;
	}

	public int getRemainingDailyCalls() {
		return remainingDailyCalls;
	}
}