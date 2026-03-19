package com.planmate.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonFormat;

@SuppressWarnings("serial")
@Entity
@Table(name = "event")
@EntityListeners(AuditingEntityListener.class)
public class Event implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "created_by", referencedColumnName = "email")
	private User user;

	private String title;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Timestamp startTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Timestamp endTime;

	private String description;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "event_participants", joinColumns = @JoinColumn(name = "event_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
	private Set<User> participants = new HashSet<>();

	@Transient
	private List<Long> participantIds;

	@Column(name = "deleted")
	private boolean deleted;

	@CreatedDate
	@Column(name = "creation_time")
	private Timestamp creationTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private Timestamp updateTime;

	@LastModifiedBy
	@Column(name = "updated_by")
	private String updatedBy;

	public Event() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Timestamp getStartTime() {
		return startTime;
	}

	public void setStartTime(Timestamp startTime) {
		this.startTime = startTime;
	}

	public Timestamp getEndTime() {
		return endTime;
	}

	public void setEndTime(Timestamp endTime) {
		this.endTime = endTime;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Set<User> getParticipants() {
		return participants;
	}

	public void setParticipants(Set<User> participants) {
		this.participants = participants;
	}

	public List<Long> getParticipantIds() {
		return participantIds;
	}

	public void setParticipantIds(List<Long> participantIds) {
		this.participantIds = participantIds;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public Timestamp getCreationTime() {
		return creationTime;
	}

	public void setCreationTime(Timestamp creationTime) {
		this.creationTime = creationTime;
	}

	public Timestamp getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(Timestamp updateTime) {
		this.updateTime = updateTime;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	@Override
	public int hashCode() {
		return Objects.hash(creationTime, deleted, description, endTime, id, startTime, title, updateTime,
				updatedBy, user);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Event other = (Event) obj;
		return Objects.equals(creationTime, other.creationTime) && deleted == other.deleted
				&& Objects.equals(description, other.description) && Objects.equals(endTime, other.endTime)
				&& Objects.equals(id, other.id)
				&& Objects.equals(startTime, other.startTime) && Objects.equals(title, other.title)
				&& Objects.equals(updateTime, other.updateTime) && Objects.equals(updatedBy, other.updatedBy)
				&& Objects.equals(user, other.user);
	}

	@Override
	public String toString() {
		return "Event [id=" + id + ", user=" + user + ", title=" + title + ", startTime=" + startTime + ", endTime="
				+ endTime + ", description=" + description + ", deleted=" + deleted
				+ ", creationTime=" + creationTime + ", updateTime=" + updateTime + ", updatedBy=" + updatedBy + "]";
	}

}
