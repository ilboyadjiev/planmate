package com.planmate.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * A named group of users, e.g. "Sports team". Groups have no owner: every member can rename the group and add or
 * remove members. Membership lives in the friend_group_member join table, so a user can be in any number of groups.
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "friend_group")
public class FriendGroup implements Serializable {

	public static final int MAX_NAME_LENGTH = 60;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = MAX_NAME_LENGTH)
	private String name;

	private Timestamp creationDate;

	// A List rather than a Set: User's equals/hashCode use mutable fields. Duplicates are prevented in the service.
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "friend_group_member", joinColumns = @JoinColumn(name = "group_id"),
			inverseJoinColumns = @JoinColumn(name = "user_id"))
	private List<User> members = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Timestamp getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Timestamp creationDate) {
		this.creationDate = creationDate;
	}

	public List<User> getMembers() {
		return members;
	}

	public void setMembers(List<User> members) {
		this.members = members;
	}

	public boolean hasMember(Long userId) {
		return members.stream().anyMatch(m -> m.getId() != null && m.getId().equals(userId));
	}
}
