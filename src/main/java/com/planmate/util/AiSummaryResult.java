package com.planmate.util;

public class AiSummaryResult {

	private final String text;

	private final boolean apiCalled;

	public AiSummaryResult(String text, boolean apiCalled) {
		this.text = text;
		this.apiCalled = apiCalled;
	}

	public String getText() {
		return text;
	}

	public boolean isApiCalled() {
		return apiCalled;
	}
}
