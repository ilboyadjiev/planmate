package com.planmate.controller;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.planmate.dto.User;
import com.planmate.service.AvatarService;
import com.planmate.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Profile pictures. Uploading and removing act on the caller's own picture. Pictures are read by URL from img
 * tags, which cannot send a login token, so GET is public; the random key in the URL (only handed out to logged-in
 * users, inside person views) is what keeps pictures from being found by trying user ids.
 */
@RestController
@RequestMapping("/api/v1/avatars")
@Tag(name = "Avatar Controller", description = "Profile pictures")
public class AvatarController {

	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,32}");
	/** A key names one picture forever (a new upload gets a new key), so browsers may keep it for a year. */
	private static final String FOREVER = "max-age=31536000, public, immutable";

	@Autowired
	private AvatarService avatarService;

	@Autowired
	private UserService userService;

	@PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Upload a new profile picture (JPEG or PNG). Returns the new avatarKey.")
	public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file, Principal principal)
			throws IOException {
		User user = caller(principal);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		String key = avatarService.store(user.getId(), file.getBytes());
		return ResponseEntity.ok(Map.of("avatarKey", key));
	}

	@DeleteMapping("/me")
	@Operation(summary = "Remove your profile picture")
	public ResponseEntity<Void> remove(Principal principal) {
		User user = caller(principal);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		avatarService.remove(user.getId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{userId}/{key}")
	@Operation(summary = "A profile picture (JPEG), by the avatarKey from a person view")
	public ResponseEntity<byte[]> image(@PathVariable Long userId, @PathVariable String key) {
		byte[] image = KEY.matcher(key).matches() ? avatarService.image(userId, key) : null;
		if (image == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header(HttpHeaders.CACHE_CONTROL, FOREVER).body(image);
	}

	private User caller(Principal principal) {
		return principal == null || principal.getName() == null ? null : userService.getUserByEmail(principal.getName());
	}
}
