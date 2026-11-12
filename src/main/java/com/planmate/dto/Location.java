package com.planmate.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;

@SuppressWarnings("serial")
@Entity
@Table(name = "location")
@EntityListeners(AuditingEntityListener.class)
public class Location implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String street;

	private String city;

	private String zipcode;

	private String country;

	private String info;

	@CreatedDate
	@Column(name = "creation_time")
	private Timestamp creationTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private Timestamp updateTime;

	@LastModifiedBy
	@Column(name = "updated_by")
	private String updatedBy;

	public Location() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
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

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
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
		return Objects.hash(city, country, creationTime, id, info, street, updateTime, updatedBy, zipcode);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Location other = (Location) obj;
		return Objects.equals(city, other.city) && Objects.equals(country, other.country)
				&& Objects.equals(creationTime, other.creationTime) && Objects.equals(id, other.id)
				&& Objects.equals(info, other.info) && Objects.equals(street, other.street)
				&& Objects.equals(updateTime, other.updateTime) && Objects.equals(updatedBy, other.updatedBy)
				&& Objects.equals(zipcode, other.zipcode);
	}

	@Override
	public String toString() {
		return "Location [id=" + id + ", street=" + street + ", city=" + city + ", zipcode=" + zipcode + ", country="
				+ country + ", info=" + info + "]";
	}

	
	
}
