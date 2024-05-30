package com.planmate.exception;

import org.springframework.http.HttpStatus;

public class BusinessLogicException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	String message;

	Exception cause;

	HttpStatus httpStatus;

	public BusinessLogicException() {
	}

	public BusinessLogicException(String message) {
		this.message = message;
	}

	public BusinessLogicException(String message, Exception cause) {
		this.message = message;
		this.cause = cause;
	}

	public BusinessLogicException(String message, Exception cause, HttpStatus httpStatus) {
		this.message = message;
		this.cause = cause;
		this.httpStatus = httpStatus;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Exception getCause() {
		return cause;
	}

	public void setCause(Exception cause) {
		this.cause = cause;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public void setHttpStatus(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

}
