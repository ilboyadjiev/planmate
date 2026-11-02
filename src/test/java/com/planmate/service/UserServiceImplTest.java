package com.planmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.planmate.dao.UserDAO;
import com.planmate.dto.Contact;
import com.planmate.dto.User;

class UserServiceImplTest {

	private UserDAO userDAO;
	private UserServiceImpl service;
	private User stored;
	private Contact storedContact;

	@BeforeEach
	void setUp() {
		userDAO = mock(UserDAO.class);
		service = new UserServiceImpl();
		ReflectionTestUtils.setField(service, "userDAO", userDAO);
		when(userDAO.updateUser(any())).thenAnswer(inv -> inv.getArgument(0));

		storedContact = new Contact();
		storedContact.setId(7L);
		storedContact.setMobile("111");
		storedContact.setCountry("DE");

		stored = new User();
		stored.setId(1L);
		stored.setEmail("alice@example.com");
		stored.setUsername("alice");
		stored.setPassword("hash");
		stored.setRole("ROLE_USER");
		stored.setFirstName("Alice");
		stored.setLastName("Old");
		stored.setContactData(storedContact);
		when(userDAO.getUserById(1L)).thenReturn(stored);
	}

	private static User request(String first, String last, String email, Contact contact) {
		User u = new User();
		u.setFirstName(first);
		u.setLastName(last);
		u.setEmail(email);
		u.setUsername("hacker");
		u.setPassword("plain");
		u.setRole("ROLE_ADMIN");
		u.setContactData(contact);
		return u;
	}

	@Test
	void updateSavesProfileFieldsButNeverIdentityOrCredentials() {
		Contact contact = new Contact();
		contact.setMobile("222");
		contact.setStreet("Main St 1");

		User result = service.updateUser(1L, request("Alicia", "New", "evil@example.com", contact), false);

		assertEquals("Alicia", result.getFirstName());
		assertEquals("New", result.getLastName());
		assertEquals("alice@example.com", result.getEmail());
		assertEquals("alice", result.getUsername());
		assertEquals("hash", result.getPassword());
		assertEquals("ROLE_USER", result.getRole());
		// The existing contact row is updated in place (no orphaned rows); PUT clears fields that were omitted
		assertSame(storedContact, result.getContactData());
		assertEquals("222", storedContact.getMobile());
		assertEquals("Main St 1", storedContact.getStreet());
		assertNull(storedContact.getCountry());
	}

	@Test
	void patchOnlyChangesProvidedFields() {
		Contact contact = new Contact();
		contact.setStreet("Main St 1");

		User result = service.updateUser(1L, request(null, "New", null, contact), true);

		assertEquals("Alice", result.getFirstName());
		assertEquals("New", result.getLastName());
		assertEquals("111", storedContact.getMobile());
		assertEquals("DE", storedContact.getCountry());
		assertEquals("Main St 1", storedContact.getStreet());
	}

	@Test
	void unknownUserReturnsNull() {
		assertNull(service.updateUser(99L, request("A", "B", null, null), false));
		verify(userDAO, never()).updateUser(any());
	}
}
