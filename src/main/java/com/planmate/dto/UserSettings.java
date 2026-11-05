package com.planmate.dto;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A user's settings (Settings page). One row per user, created on their first change; until then, and for every
 * column left empty, the defaults below apply, which are exactly how PlanMate behaved before settings existed.
 *
 * Values are stored as short lower-case words (see UserSettingsService for the allowed ones) and tag lists as
 * comma-separated normalised tags ("#music,#food").
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

	public static final String WEEK_MONDAY = "monday";
	public static final String WEEK_SUNDAY = "sunday";
	public static final String TIME_24H = "24h";
	public static final String TIME_12H = "12h";
	public static final String VIEW_AUTO = "auto";
	public static final String VISIBLE_FRIENDS = "friends";
	public static final String VISIBLE_EVERYONE = "everyone";
	public static final String VISIBLE_ONLY_ME = "only_me";
	public static final String GROUPS_FRIENDS = "friends";
	public static final String GROUPS_NOBODY = "nobody";

	@Id
	@Column(name = "user_id")
	private Long userId;

	/** First day of the week in the calendar: monday or sunday. */
	@Column(name = "week_start", length = 10)
	private String weekStart;

	/** How times are shown: 24h or 12h. */
	@Column(name = "time_format", length = 4)
	private String timeFormat;

	/** The calendar's first view: auto (day on phones, month on larger screens), month, week, day or agenda. */
	@Column(name = "calendar_view", length = 10)
	private String calendarView;

	/** Who sees which public events you are going to: friends, everyone or only_me. */
	@Column(name = "going_visibility", length = 10)
	private String goingVisibility;

	/** Whether you may appear in other people's friend suggestions. */
	@Column(name = "show_in_suggestions")
	private Boolean showInSuggestions;

	/** Who may add you to friend groups: friends or nobody. */
	@Column(name = "group_adds", length = 10)
	private String groupAdds;

	/** Whether people who are not your friends can find you by name in people search. */
	@Column(name = "searchable")
	private Boolean searchable;

	/**
	 * Whether the AI Game plan is shown and may be generated. When off, the app hides it and the server never sends
	 * the user's events to Gemini.
	 */
	@Column(name = "ai_game_plan")
	private Boolean aiGamePlan;

	/**
	 * The secret in the user's calendar subscription link (/api/v2/calendar/{key}.ics), which calendar apps poll
	 * without logging in. Null until they create one; changed when they reset it, which cuts off the old link.
	 */
	@Column(name = "calendar_key", length = 64, unique = true)
	private String calendarKey;

	/** Topics boosted on Discover. */
	@Column(name = "liked_tags", length = 1000)
	private String likedTags;

	/** Topics hidden from Discover's "For you" and "Around me". */
	@Column(name = "hidden_tags", length = 1000)
	private String hiddenTags;

	public UserSettings() {
	}

	public UserSettings(Long userId) {
		this.userId = userId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getWeekStart() {
		return weekStart == null ? WEEK_MONDAY : weekStart;
	}

	public void setWeekStart(String weekStart) {
		this.weekStart = weekStart;
	}

	public String getTimeFormat() {
		return timeFormat == null ? TIME_24H : timeFormat;
	}

	public void setTimeFormat(String timeFormat) {
		this.timeFormat = timeFormat;
	}

	public String getCalendarView() {
		return calendarView == null ? VIEW_AUTO : calendarView;
	}

	public void setCalendarView(String calendarView) {
		this.calendarView = calendarView;
	}

	public String getGoingVisibility() {
		return goingVisibility == null ? VISIBLE_FRIENDS : goingVisibility;
	}

	public void setGoingVisibility(String goingVisibility) {
		this.goingVisibility = goingVisibility;
	}

	public boolean isShowInSuggestions() {
		return showInSuggestions == null || showInSuggestions;
	}

	public void setShowInSuggestions(Boolean showInSuggestions) {
		this.showInSuggestions = showInSuggestions;
	}

	public String getGroupAdds() {
		return groupAdds == null ? GROUPS_FRIENDS : groupAdds;
	}

	public void setGroupAdds(String groupAdds) {
		this.groupAdds = groupAdds;
	}

	public boolean isSearchable() {
		return searchable == null || searchable;
	}

	public void setSearchable(Boolean searchable) {
		this.searchable = searchable;
	}

	public boolean isAiGamePlan() {
		return aiGamePlan == null || aiGamePlan;
	}

	public void setAiGamePlan(Boolean aiGamePlan) {
		this.aiGamePlan = aiGamePlan;
	}

	public String getCalendarKey() {
		return calendarKey;
	}

	public void setCalendarKey(String calendarKey) {
		this.calendarKey = calendarKey;
	}

	public String getLikedTags() {
		return likedTags;
	}

	public void setLikedTags(String likedTags) {
		this.likedTags = likedTags;
	}

	public String getHiddenTags() {
		return hiddenTags;
	}

	public void setHiddenTags(String hiddenTags) {
		this.hiddenTags = hiddenTags;
	}
}
