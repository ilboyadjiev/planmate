package com.planmate.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.planmate.dao.AvatarDao;
import com.planmate.dao.EventDAO;
import com.planmate.dto.Contact;
import com.planmate.dto.Event;
import com.planmate.dto.FriendGroup;
import com.planmate.dto.Friendship;
import com.planmate.dto.User;
import com.planmate.dto.UserAvatar;
import com.planmate.dto2.EventDoc;
import com.planmate.util.PublicViews;

/**
 * "Download my data" (Settings): everything PlanMate stores about the user, as one JSON document. Other people
 * appear by name and username only, as everywhere else in the app.
 *
 * Kept in the browser only, so not included: recent searches, today's AI Game plan, the cookie choice.
 */
@Service
public class AccountExportService {

	private final UserService userService;
	private final UserSettingsService settings;
	private final FriendshipService friendshipService;
	private final FriendGroupService friendGroupService;
	private final AvatarDao avatarDao;
	private final EventDAO legacyEvents;
	private final MongoTemplate mongo;

	public AccountExportService(UserService userService, UserSettingsService settings,
			FriendshipService friendshipService, FriendGroupService friendGroupService, AvatarDao avatarDao,
			EventDAO legacyEvents, MongoTemplate mongo) {
		this.userService = userService;
		this.settings = settings;
		this.friendshipService = friendshipService;
		this.friendGroupService = friendGroupService;
		this.avatarDao = avatarDao;
		this.legacyEvents = legacyEvents;
		this.mongo = mongo;
	}

	@Transactional
	public Map<String, Object> export(String email) {
		User user = userService.getUserByEmail(email);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("exportedAt", Instant.now().toString());
		data.put("about", "Everything PlanMate stores about your account. Other people are shown by name only.");
		data.put("account", account(user));
		data.put("settings", settings.view(user));
		data.put("profilePhoto", photo(user));
		data.put("friends", friends(user));
		data.put("groups", groups(email));
		data.put("events", events(user));
		return data;
	}

	private static Map<String, Object> account(User user) {
		Map<String, Object> account = new LinkedHashMap<>();
		account.put("id", user.getId());
		account.put("username", user.getUsername());
		account.put("firstName", user.getFirstName());
		account.put("lastName", user.getLastName());
		account.put("email", user.getEmail());
		account.put("signIn", UserSettingsService.hasPasswordLogin(user) ? "password" : "google");
		Contact contact = user.getContactData();
		Map<String, Object> contactData = new LinkedHashMap<>();
		if (contact != null) {
			contactData.put("mobile", contact.getMobile());
			contactData.put("street", contact.getStreet());
			contactData.put("zipcode", contact.getZipcode());
			contactData.put("city", contact.getCity());
			contactData.put("country", contact.getCountry());
		}
		account.put("contact", contactData);
		return account;
	}

	private Map<String, Object> photo(User user) {
		UserAvatar avatar = avatarDao.get(user.getId());
		if (avatar == null) {
			return null;
		}
		Map<String, Object> photo = new LinkedHashMap<>();
		photo.put("type", "image/jpeg");
		photo.put("updatedAt", avatar.getUpdatedAt() == null ? null : avatar.getUpdatedAt().toString());
		photo.put("base64", Base64.getEncoder().encodeToString(avatar.getImage()));
		return photo;
	}

	private List<Map<String, Object>> friends(User user) {
		List<Map<String, Object>> friends = new ArrayList<>();
		for (Friendship f : friendshipService.getFriendshipList(user.getEmail())) {
			if (f.getUserA() == null || f.getUserB() == null) {
				continue;
			}
			boolean iAmA = user.getId().equals(f.getUserA().getId());
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("person", PublicViews.person(iAmA ? f.getUserB() : f.getUserA()));
			entry.put("status", f.getStatus());
			entry.put("requestedBy", iAmA ? "you" : "them");
			entry.put("requestedAt", f.getRequestDate() == null ? null : f.getRequestDate().toInstant().toString());
			entry.put("acceptedAt", f.getAcceptDate() == null ? null : f.getAcceptDate().toInstant().toString());
			friends.add(entry);
		}
		return friends;
	}

	private List<Map<String, Object>> groups(String email) {
		return friendGroupService.groupsOf(email).stream().map(PublicViews::group).collect(Collectors.toList());
	}

	private Map<String, Object> events(User user) {
		String id = String.valueOf(user.getId());
		Sort soonest = Sort.by("startTime").ascending();
		Map<String, Object> events = new LinkedHashMap<>();
		events.put("created", mongo.find(new Query(Criteria.where("creatorId").is(user.getEmail())).with(soonest),
				EventDoc.class).stream().map(e -> event(e, id)).collect(Collectors.toList()));
		events.put("invitedTo", mongo.find(new Query(Criteria.where("participantIds").is(id)
				.and("creatorId").ne(user.getEmail())).with(soonest), EventDoc.class).stream()
				.map(e -> event(e, id)).collect(Collectors.toList()));
		events.put("joined", mongo.find(new Query(Criteria.where("attendeeIds").is(id)).with(soonest),
				EventDoc.class).stream().map(e -> event(e, id)).collect(Collectors.toList()));
		events.put("answeredMaybe", mongo.find(new Query(Criteria.where("maybeIds").is(id)).with(soonest),
				EventDoc.class).stream().map(e -> event(e, id)).collect(Collectors.toList()));
		events.put("fromTheOldCalendar", legacyEvents.getAllEventsForUsername(user.getUsername()).stream()
				.map(AccountExportService::legacyEvent).collect(Collectors.toList()));
		return events;
	}

	private static Map<String, Object> event(EventDoc e, String myId) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("title", e.getTitle());
		event.put("description", e.getDescription());
		event.put("start", e.getStartTime() == null ? null : e.getStartTime().toString());
		event.put("end", e.getEndTime() == null ? null : e.getEndTime().toString());
		event.put("location", e.getLocation());
		event.put("tags", e.getTags());
		event.put("public", Boolean.TRUE.equals(e.getIsPublic()));
		if (e.getResponses() != null && e.getResponses().containsKey(myId)) {
			event.put("yourAnswer", e.getResponses().get(myId));
		}
		return event;
	}

	private static Map<String, Object> legacyEvent(Event e) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("title", e.getTitle());
		event.put("description", e.getDescription());
		event.put("start", e.getStartTime() == null ? null : e.getStartTime().toInstant().toString());
		event.put("end", e.getEndTime() == null ? null : e.getEndTime().toInstant().toString());
		return event;
	}
}
