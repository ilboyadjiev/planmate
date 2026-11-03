package com.planmate.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.planmate.dto.Contact;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;

class PublicViewsTest {

	private static User user(long id, String email) {
		User u = new User();
		u.setId(id);
		u.setEmail(email);
		u.setUsername("user" + id);
		u.setFirstName("First" + id);
		u.setRole("ROLE_USER");
		Contact contact = new Contact();
		contact.setMobile("+49 170 0000000");
		contact.setStreet("Somestreet 1");
		u.setContactData(contact);
		return u;
	}

	@Test
	void searchShowsNameAndUsernameOnly() {
		Map<String, Object> view = PublicViews.person(user(1, "alice@example.com"));

		assertEquals(Map.of("id", 1L, "username", "user1", "firstName", "First1"),
				Map.of("id", view.get("id"), "username", view.get("username"), "firstName", view.get("firstName")));
		assertFalse(view.containsKey("email"));
		assertFalse(view.toString().contains("170"));
		assertFalse(view.toString().contains("Somestreet"));
		assertFalse(view.toString().contains("ROLE_USER"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void onlyAcceptedFriendsSeeEachOthersEmailAndNobodySeesContactDetails() {
		Friendship friendship = new Friendship();
		friendship.setUserA(user(1, "alice@example.com"));
		friendship.setUserB(user(2, "bob@example.com"));

		friendship.setStatus("pending");
		Map<String, Object> pending = PublicViews.friendship(friendship);
		assertFalse(((Map<String, Object>) pending.get("userA")).containsKey("email"));

		friendship.setStatus("accepted");
		Map<String, Object> accepted = PublicViews.friendship(friendship);
		assertEquals("bob@example.com", ((Map<String, Object>) accepted.get("userB")).get("email"));
		assertFalse(accepted.toString().contains("170"));
		assertFalse(accepted.toString().contains("Somestreet"));
	}
}
