package com.planmate.discovery;

/** A message that can never be applied, however often it is retried: it goes straight to discovery.events.dlq. */
public class InvalidDiscoveryMessageException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidDiscoveryMessageException(String message) {
		super(message);
	}
}
