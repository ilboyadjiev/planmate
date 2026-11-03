package com.planmate.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.planmate.dto2.EventDoc;
import com.planmate.util.CityTags;

/**
 * Tags events with their city (e.g. "#munich") when they are created or edited, so they show up in "Around me"
 * and under city tags. The city comes from the location, the title or the description (see CityTags).
 *
 * The event remembers the tag it was given automatically (cityTag), so that:
 * - when the event moves to another city, the old city tag is replaced by the new one,
 * - when the creator removes the city tag by hand, it is not added again on the next save.
 */
@Service
public class CityTagService {

	/**
	 * Sets the city tag on an event that is about to be saved.
	 *
	 * @param event         the event as it will be saved (its tags are what the creator sent)
	 * @param previousCityTag the city tag added automatically last time, or null for a new event
	 */
	public void apply(EventDoc event, String previousCityTag) {
		String cityTag = CityTags.forEvent(event).orElse(null);
		List<String> tags = event.getTags() == null ? new ArrayList<>() : new ArrayList<>(event.getTags());

		if (!Objects.equals(cityTag, previousCityTag)) {
			if (previousCityTag != null) {
				tags.remove(previousCityTag);
			}
			if (cityTag != null && !tags.contains(cityTag)) {
				tags.add(cityTag);
			}
		}
		event.setTags(tags);
		event.setCityTag(cityTag);
	}
}
