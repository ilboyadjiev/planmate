package com.planmate.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.planmate.dao.UserDAO;
import com.planmate.dao.UserSettingsDao;
import com.planmate.dto.Contact;
import com.planmate.dto.User;
import com.planmate.dto.UserSettings;

/**
 * Reading and changing users' settings (Settings page). Other services ask here before showing someone's data:
 * which public events they go to (going visibility), whether they appear in suggestions or people search, whether
 * they may be added to groups, and which topics they like or hide on Discover.
 *
 * The home city for "Around me" is part of the settings API but stays stored in the user's contact data, where the
 * feed and suggestions already read it.
 */
@Service
public class UserSettingsService {

	/** Settings that only ever answer with defaults, for code built without a database (unit tests). */
	public static final UserSettingsService DEFAULTS_ONLY = new UserSettingsService(null, null);

	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	static final int MAX_TAGS = 20;
	static final int MAX_TAG_LENGTH = 30;
	static final int MAX_CITY_LENGTH = 80;

	private static final Set<String> WEEK_STARTS = set(UserSettings.WEEK_MONDAY, UserSettings.WEEK_SUNDAY);
	private static final Set<String> TIME_FORMATS = set(UserSettings.TIME_24H, UserSettings.TIME_12H);
	private static final Set<String> CALENDAR_VIEWS = set(UserSettings.VIEW_AUTO, "month", "week", "day", "agenda");
	private static final Set<String> VISIBILITIES = set(UserSettings.VISIBLE_FRIENDS, UserSettings.VISIBLE_EVERYONE,
			UserSettings.VISIBLE_ONLY_ME);
	private static final Set<String> GROUP_ADDS = set(UserSettings.GROUPS_FRIENDS, UserSettings.GROUPS_NOBODY);

	private final UserSettingsDao dao;
	private final UserDAO userDAO;

	public UserSettingsService(UserSettingsDao dao, UserDAO userDAO) {
		this.dao = dao;
		this.userDAO = userDAO;
	}

	/** The user's settings; defaults when they never changed any. */
	@Transactional
	public UserSettings of(Long userId) {
		UserSettings stored = dao == null || userId == null ? null : dao.get(userId);
		return stored != null ? stored : new UserSettings(userId);
	}

	/** Settings of several users at once, every one of them present (defaults for those without a row). */
	@Transactional
	public Map<Long, UserSettings> of(Collection<Long> userIds) {
		Map<Long, UserSettings> result = new HashMap<>();
		if (userIds == null) {
			return result;
		}
		List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
		if (dao != null) {
			dao.getAll(ids).forEach(s -> result.put(s.getUserId(), s));
		}
		ids.forEach(id -> result.computeIfAbsent(id, UserSettings::new));
		return result;
	}

	/** Everything the Settings page shows, for the user themselves. */
	@Transactional
	public Map<String, Object> view(User user) {
		UserSettings s = of(user.getId());
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("weekStart", s.getWeekStart());
		view.put("timeFormat", s.getTimeFormat());
		view.put("calendarView", s.getCalendarView());
		view.put("goingVisibility", s.getGoingVisibility());
		view.put("showInSuggestions", s.isShowInSuggestions());
		view.put("groupAdds", s.getGroupAdds());
		view.put("searchable", s.isSearchable());
		view.put("aiGamePlan", s.isAiGamePlan());
		view.put("calendarFeedKey", s.getCalendarKey());
		view.put("likedTags", likedTags(s));
		view.put("hiddenTags", hiddenTags(s));
		view.put("homeCity", user.getContactData() == null ? null : user.getContactData().getCity());
		view.put("passwordLogin", hasPasswordLogin(user));
		return view;
	}

	/**
	 * Changes the given settings and leaves the others as they are. Unknown names and values that are not allowed
	 * are refused (400) before anything is saved. Returns the full settings afterwards.
	 */
	@Transactional
	public Map<String, Object> update(Long userId, Map<String, Object> changes) {
		User user = userDAO.getUserById(userId);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
		}
		if (changes == null || changes.isEmpty()) {
			return view(user);
		}
		UserSettings s = of(userId);
		boolean cityChanged = false;
		String city = null;
		for (Map.Entry<String, Object> change : changes.entrySet()) {
			Object value = change.getValue();
			switch (change.getKey()) {
			case "weekStart":
				s.setWeekStart(oneOf(change.getKey(), value, WEEK_STARTS));
				break;
			case "timeFormat":
				s.setTimeFormat(oneOf(change.getKey(), value, TIME_FORMATS));
				break;
			case "calendarView":
				s.setCalendarView(oneOf(change.getKey(), value, CALENDAR_VIEWS));
				break;
			case "goingVisibility":
				s.setGoingVisibility(oneOf(change.getKey(), value, VISIBILITIES));
				break;
			case "groupAdds":
				s.setGroupAdds(oneOf(change.getKey(), value, GROUP_ADDS));
				break;
			case "showInSuggestions":
				s.setShowInSuggestions(bool(change.getKey(), value));
				break;
			case "searchable":
				s.setSearchable(bool(change.getKey(), value));
				break;
			case "aiGamePlan":
				s.setAiGamePlan(bool(change.getKey(), value));
				break;
			case "likedTags":
				s.setLikedTags(joinTags(tags(change.getKey(), value)));
				break;
			case "hiddenTags":
				s.setHiddenTags(joinTags(tags(change.getKey(), value)));
				break;
			case "homeCity":
				cityChanged = true;
				city = city(value);
				break;
			default:
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown setting: " + change.getKey());
			}
		}
		// A topic is either liked or hidden; the one changed last wins
		Set<String> hidden = new LinkedHashSet<>(hiddenTags(s));
		if (changes.containsKey("likedTags")) {
			hidden.removeAll(likedTags(s));
			s.setHiddenTags(joinTags(new ArrayList<>(hidden)));
		} else if (changes.containsKey("hiddenTags")) {
			List<String> liked = new ArrayList<>(likedTags(s));
			liked.removeAll(hidden);
			s.setLikedTags(joinTags(liked));
		}
		dao.save(s);

		if (cityChanged) {
			Contact contact = user.getContactData();
			if (contact == null) {
				contact = new Contact();
				user.setContactData(contact);
			}
			contact.setCity(city);
			userDAO.updateUser(user);
		}
		return view(user);
	}

	/**
	 * Creates the user's calendar subscription key, or a new one when reset is true (the old link stops working).
	 * Returns the key.
	 */
	@Transactional
	public String calendarFeedKey(Long userId, boolean reset) {
		UserSettings s = of(userId);
		if (s.getCalendarKey() == null || reset) {
			byte[] bytes = new byte[24];
			RANDOM.nextBytes(bytes);
			s.setCalendarKey(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
			dao.save(s);
		}
		return s.getCalendarKey();
	}

	/** Turns the calendar subscription off: the link stops working. */
	@Transactional
	public void removeCalendarFeed(Long userId) {
		UserSettings s = of(userId);
		if (s.getCalendarKey() != null) {
			s.setCalendarKey(null);
			dao.save(s);
		}
	}

	/** The user a calendar subscription key belongs to, or null. */
	@Transactional
	public Long userIdForCalendarKey(String key) {
		if (dao == null || key == null || !key.matches("[A-Za-z0-9_-]{20,64}")) {
			return null;
		}
		UserSettings s = dao.getByCalendarKey(key);
		return s == null ? null : s.getUserId();
	}

	/** Removes the user's settings (account deletion). */
	@Transactional
	public void deleteFor(Long userId) {
		if (dao != null && userId != null) {
			dao.delete(userId);
		}
	}

	public static List<String> likedTags(UserSettings s) {
		return splitTags(s.getLikedTags());
	}

	public static List<String> hiddenTags(UserSettings s) {
		return splitTags(s.getHiddenTags());
	}

	/**
	 * Whether the user logs in with a PlanMate password. Accounts made through "Sign in with Google" have a random
	 * one they never saw. Older Google accounts were not marked, but always got their email as username.
	 */
	public static boolean hasPasswordLogin(User user) {
		if (user.getGoogleAccount() != null) {
			return !user.getGoogleAccount();
		}
		return user.getUsername() == null || !user.getUsername().equalsIgnoreCase(user.getEmail());
	}

	/** Who may see which public events this user goes to. */
	public static boolean showsGoingTo(UserSettings owner, boolean viewerIsSelf, boolean viewerIsFriend) {
		switch (owner.getGoingVisibility()) {
		case UserSettings.VISIBLE_EVERYONE:
			return true;
		case UserSettings.VISIBLE_ONLY_ME:
			return viewerIsSelf;
		default:
			return viewerIsSelf || viewerIsFriend;
		}
	}

	private static List<String> splitTags(String joined) {
		if (StringUtils.isBlank(joined)) {
			return new ArrayList<>();
		}
		return Arrays.stream(joined.split(",")).map(String::trim).filter(StringUtils::isNotEmpty)
				.collect(Collectors.toList());
	}

	private static String joinTags(List<String> tags) {
		return tags.isEmpty() ? null : String.join(",", tags);
	}

	private static String oneOf(String name, Object value, Set<String> allowed) {
		String text = value instanceof String ? ((String) value).trim().toLowerCase() : null;
		if (text == null || !allowed.contains(text)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					name + " must be one of " + String.join(", ", allowed) + ".");
		}
		return text;
	}

	private static Boolean bool(String name, Object value) {
		if (!(value instanceof Boolean)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be true or false.");
		}
		return (Boolean) value;
	}

	private static List<String> tags(String name, Object value) {
		if (value == null) {
			return new ArrayList<>();
		}
		if (!(value instanceof Collection)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be a list of tags.");
		}
		Set<String> tags = new LinkedHashSet<>();
		for (Object item : (Collection<?>) value) {
			String tag = item instanceof String ? FeedService.normalizeTag(((String) item).replaceAll("\\s+", "")) : null;
			if (tag == null || tag.length() < 2) {
				continue;
			}
			if (tag.length() > MAX_TAG_LENGTH || !tag.substring(1).matches("[\\p{L}\\p{N}_-]+")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Tags may only use letters, numbers, - and _ (at most " + MAX_TAG_LENGTH + " characters).");
			}
			tags.add(tag);
		}
		if (tags.size() > MAX_TAGS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_TAGS + " topics.");
		}
		return new ArrayList<>(tags);
	}

	private static String city(Object value) {
		if (value != null && !(value instanceof String)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "homeCity must be text.");
		}
		String city = StringUtils.normalizeSpace((String) value);
		if (StringUtils.isEmpty(city)) {
			return null;
		}
		if (city.length() > MAX_CITY_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"City names can be at most " + MAX_CITY_LENGTH + " characters.");
		}
		return city;
	}

	private static Set<String> set(String... values) {
		return new LinkedHashSet<>(Arrays.asList(values));
	}
}
