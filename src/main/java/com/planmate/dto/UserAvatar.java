package com.planmate.dto;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A user's profile picture: one small square JPEG (AvatarService.SIZE pixels, a few KB). Kept out of the user row
 * so the many queries that load users never carry image bytes; it is fetched on its own by URL and cached.
 */
@Entity
@Table(name = "user_avatar")
public class UserAvatar {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "image", nullable = false)
	private byte[] image;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UserAvatar() {
	}

	public UserAvatar(Long userId, byte[] image, Instant updatedAt) {
		this.userId = userId;
		this.image = image;
		this.updatedAt = updatedAt;
	}

	public Long getUserId() {
		return userId;
	}

	public byte[] getImage() {
		return image;
	}

	public void setImage(byte[] image) {
		this.image = image;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
