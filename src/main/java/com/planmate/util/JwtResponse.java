package com.planmate.util;

public class JwtResponse {
	private String accessToken;
	private String refreshToken;
	private String username;
	private String firstName;
	private String email;

	public JwtResponse(String accessToken, String refreshToken, String username, String firstName, String email) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.username = username;
		this.firstName = firstName;
		this.email = email;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
}