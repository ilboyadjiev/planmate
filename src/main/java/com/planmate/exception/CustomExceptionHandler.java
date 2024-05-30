package com.planmate.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

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
}
