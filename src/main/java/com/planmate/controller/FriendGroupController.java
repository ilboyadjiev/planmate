package com.planmate.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dto.FriendGroup;
import com.planmate.service.FriendGroupService;
import com.planmate.util.PublicViews;

/**
 * Friend groups of the logged-in user (see FriendGroupService for the rules).
 *
 * Request bodies: { "name": "...", "memberIds": [1, 2] } to create, { "name": "..." } to rename,
 * { "userIds": [3] } to add members.
 */
@RestController
@RequestMapping("/api/v1/groups")
public class FriendGroupController {

	public static class GroupRequest {
		public String name;
		public List<Long> memberIds;
		public List<Long> userIds;
	}

	private final FriendGroupService groupService;

	public FriendGroupController(FriendGroupService groupService) {
		this.groupService = groupService;
	}

	private static String email(Principal principal) {
		return principal == null ? null : principal.getName();
	}

	@GetMapping
	public List<Map<String, Object>> myGroups(Principal principal) {
		return groupService.groupsOf(email(principal)).stream().map(PublicViews::group).collect(Collectors.toList());
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@RequestBody GroupRequest body, Principal principal) {
		FriendGroup group = groupService.create(email(principal), body.name, body.memberIds);
		return new ResponseEntity<>(PublicViews.group(group), HttpStatus.CREATED);
	}

	@PatchMapping("/{id}")
	public Map<String, Object> rename(@PathVariable Long id, @RequestBody GroupRequest body, Principal principal) {
		return PublicViews.group(groupService.rename(email(principal), id, body.name));
	}

	@PostMapping("/{id}/members")
	public Map<String, Object> addMembers(@PathVariable Long id, @RequestBody GroupRequest body, Principal principal) {
		return PublicViews.group(groupService.addMembers(email(principal), id, body.userIds));
	}

	/** Remove a member; removing yourself leaves the group. 204 when that deleted the group. */
	@DeleteMapping("/{id}/members/{userId}")
	public ResponseEntity<Map<String, Object>> removeMember(@PathVariable Long id, @PathVariable Long userId,
			Principal principal) {
		FriendGroup group = groupService.removeMember(email(principal), id, userId);
		return group == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(PublicViews.group(group));
	}
}
