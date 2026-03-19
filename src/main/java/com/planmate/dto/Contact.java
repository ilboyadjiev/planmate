package com.planmate.dto;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "contact")
//@ApiModel(description = "Details about the contact")
public class Contact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	//@ApiModelProperty(notes = "The unique ID of the contact")
	private Long id;

	//@ApiModelProperty(notes = "The mobile phone number of the contact")
	private String mobile;

	//@ApiModelProperty(notes = "The street of the address")
	private String street;

	//@ApiModelProperty(notes = "The zipcode of the address")
	private String zipcode;

	//@ApiModelProperty(notes = "The country of the address")
	private String country;

	public Contact() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getZipcode() {
		return zipcode;
	}

	public void setZipcode(String zipcode) {
		this.zipcode = zipcode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	@Override
	public int hashCode() {
		return Objects.hash(country, id, mobile, street, zipcode);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Contact other = (Contact) obj;
		return Objects.equals(country, other.country) && Objects.equals(id, other.id)
				&& Objects.equals(mobile, other.mobile) && Objects.equals(street, other.street)
				&& Objects.equals(zipcode, other.zipcode);
	}

	@Override
	public String toString() {
		return "Contact [id=" + id + ", mobile=" + mobile + ", street=" + street + ", zipcode=" + zipcode + ", country="
				+ country + "]";
	}

}
