package com.planmate.util;

public class JwtResponse {
	private String accessToken;
	private String refreshToken;
	private String username;
	private String firstName;

	public JwtResponse(String accessToken, String refreshToken, String username, String firstName) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.username = username;
		this.firstName = firstName;
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
}