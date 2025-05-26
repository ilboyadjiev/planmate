package com.planmate.dto;

import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@SuppressWarnings("serial")
@Entity
@Table(name = "appuser")
//@ApiModel(description = "Details about the user")
public class User implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	//@ApiModelProperty(notes = "The unique ID of the user")
	private Long id;

	@Column(name = "firstname")
	//@ApiModelProperty(notes = "The user's first name")
	private String firstName;

	@Column(name = "lastname")
	//@ApiModelProperty(notes = "The user's last name")
	private String lastName;

	@Column(name = "email", unique = true)
	//@ApiModelProperty(notes = "The email of the contact")
	private String email;

	@Column(name = "password")
	//@ApiModelProperty(notes = "Password")
	private String password;

	@Column(name = "role")
	//@ApiModelProperty(notes = "The role of the user")
	private String role;

	@Column(name = "username", unique = true)
	//@ApiModelProperty(notes = "A custom display name")
	private String username;

	@OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "contact_id", referencedColumnName = "id")
	//@ApiModelProperty(notes = "The user's contact information")
    private Contact contactData;

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

}
