package com.planmate.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("serial")
@Entity
@Table(name = "appuser")
//@ApiModel(description = "Details about the user")
public class User implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	// @ApiModelProperty(notes = "The unique ID of the user")
	private Long id;

	@Column(name = "firstname")
	// @ApiModelProperty(notes = "The user's first name")
	private String firstName;

	@Column(name = "lastname")
	// @ApiModelProperty(notes = "The user's last name")
	private String lastName;

	@Column(name = "email", unique = true)
	// @ApiModelProperty(notes = "The email of the contact")
	private String email;

	@Column(name = "password")
	// @ApiModelProperty(notes = "Password")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	@Column(name = "role")
	// @ApiModelProperty(notes = "The role of the user")
	private String role;

	@Column(name = "username", unique = true)
	// @ApiModelProperty(notes = "A custom display name")
	private String username;

	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "contact_id", referencedColumnName = "id")
	// @ApiModelProperty(notes = "The user's contact information")
	private Contact contactData;

	@JsonIgnore
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Event> createdEvents = new ArrayList<>();

	@JsonIgnore
	@ManyToMany(mappedBy = "participants")
	private Set<Event> participatingEvents = new HashSet<>();

	public User() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public Contact getContactData() {
		return contactData;
	}

	public void setContactData(Contact contactData) {
		this.contactData = contactData;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public List<Event> getCreatedEvents() {
		return createdEvents;
	}

	public void setCreatedEvents(List<Event> createdEvents) {
		this.createdEvents = createdEvents;
	}

	public Set<Event> getParticipatingEvents() {
		return participatingEvents;
	}

	public void setParticipatingEvents(Set<Event> participatingEvents) {
		this.participatingEvents = participatingEvents;
	}

	@Override
	public int hashCode() {
		return Objects.hash(contactData, email, firstName, id, lastName, password, role, username);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		User other = (User) obj;
		return Objects.equals(contactData, other.contactData) && Objects.equals(email, other.email)
				&& Objects.equals(firstName, other.firstName) && Objects.equals(id, other.id)
				&& Objects.equals(lastName, other.lastName) && Objects.equals(password, other.password)
				&& Objects.equals(role, other.role) && Objects.equals(username, other.username);
	}

	@Override
	public String toString() {
		return "User [id=" + id + ", firstName=" + firstName + ", lastName=" + lastName + ", email=" + email
				+ ", password=" + password + ", role=" + role + ", username=" + username + ", contactData="
				+ contactData + "]";
	}

}
