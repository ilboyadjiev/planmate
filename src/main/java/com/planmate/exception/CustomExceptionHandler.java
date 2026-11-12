package com.planmate.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class CustomExceptionHandler {

	@ExceptionHandler(BusinessLogicException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ResponseEntity<Object> handleBusinessLogicException(BusinessLogicException ex) {
		Map<String, Object> body = new HashMap<>();
		HttpStatus errorStatus;
        body.put("timestamp", LocalDateTime.now());
        if (ex.getHttpStatus() != null) {
        	body.put("status", ex.getHttpStatus().value());
        	body.put("status reason", ex.getHttpStatus().getReasonPhrase());
        	errorStatus = ex.getHttpStatus();
        } else {
        	body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        	body.put("status reason", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        	errorStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex.getMessage() != null) {
        	body.put("error", ex.getMessage());
        }
        if (ex.getCause() != null) {
        	body.put("Caused by", ex.getCause().getMessage());
        }
        return new ResponseEntity<>(body, errorStatus);
	}

	/** An upload over spring.servlet.multipart.max-file-size: 413 with a readable message, not a 500. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Object> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
		body.put("error", "The picture is too large.");
		return new ResponseEntity<>(body, HttpStatus.PAYLOAD_TOO_LARGE);
	}

	/**
	 * Refusals the services raise on purpose ("You can only add your friends.", "Bob doesn't want to be added to
	 * groups."): their reason is written for people, so it is sent as both error and message. Unexpected exceptions
	 * keep Spring's default body, which leaves internal details out.
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", ex.getStatusCode().value());
		String reason = ex.getReason();
		if (reason == null) {
			// getStatusCode() returns HttpStatusCode (Spring 6); resolve to a standard status for its phrase.
			HttpStatus resolved = HttpStatus.resolve(ex.getStatusCode().value());
			reason = resolved != null ? resolved.getReasonPhrase() : "";
		}
		body.put("error", reason);
		body.put("message", reason);
		return new ResponseEntity<>(body, ex.getResponseHeaders(), ex.getStatusCode());
	}
}
