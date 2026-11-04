package com.planmate.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.planmate.dto2.EventDoc;

/**
 * Turns text into search words, the same way for events and for what people type into the search box:
 * lower case, accents removed ("Café München" -> "cafe", "munchen"), split on anything that is not a letter or digit,
 * one-letter words and common English/German filler words ("the", "und", ...) dropped.
 *
 * Words typed into the search match event words that start with them ("conc" finds "concert"). They are also
 * lightly stemmed, so "concerts" and "Konzerte" still find "concert" and "Konzert".
 */
public final class SearchText {

	static final int MAX_TERMS = 300;

	private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
			// English
			"the", "an", "and", "or", "of", "in", "at", "on", "to", "for", "with", "by", "is", "are", "my", "our",
			"your", "this", "that", "from", "it", "we",
			// German
			"der", "die", "das", "und", "oder", "im", "am", "an", "mit", "zum", "zur", "von", "vom", "bei", "auf", "fur",
			"ein", "eine", "einen", "dem", "den", "des", "ist", "wir"));

	/** Endings removed by stem(), longest first. */
	private static final String[] SUFFIXES = { "es", "en", "er", "s", "e" };
	private static final int MIN_STEM = 4;

	private SearchText() {
	}

	/** The words in a text, in order and with repeats (so they can be counted). */
	public static List<String> tokens(String text) {
		if (StringUtils.isBlank(text)) {
			return Collections.emptyList();
		}
		return Arrays.stream(CityTags.normalise(text).split("[^\\p{L}\\p{N}]+"))
				.filter(word -> word.length() >= 2 && !STOP_WORDS.contains(word))
				.collect(Collectors.toList());
	}

	/** The distinct words in a search query, at most max. */
	public static List<String> queryTokens(String query, int max) {
		return tokens(query).stream().distinct().limit(max).collect(Collectors.toList());
	}

	/**
	 * A light stem for matching: "concerts" -> "concert", "konzerte" -> "konzert", "games" -> "game". Removes one
	 * common ending when at least four letters remain, so short words ("beer", "bar") stay as they are.
	 */
	public static String stem(String word) {
		for (String suffix : SUFFIXES) {
			if (word.endsWith(suffix) && word.length() - suffix.length() >= MIN_STEM) {
				return word.substring(0, word.length() - suffix.length());
			}
		}
		return word;
	}

	/**
	 * The words an event can be found by: from its title, tags, location and description. Stored on the event
	 * (searchTerms) and indexed, so a search only looks at events containing a word that starts with what was typed.
	 */
	public static List<String> terms(EventDoc event) {
		Set<String> terms = new LinkedHashSet<>();
		Stream.of(event.getTitle(), String.join(" ", tagsOf(event)), placeOf(event), event.getDescription())
				.map(SearchText::tokens)
				.forEach(terms::addAll);
		return terms.stream().limit(MAX_TERMS).collect(Collectors.toList());
	}

	/** The location as one text: place name, street, city and country. */
	public static String placeOf(EventDoc event) {
		EventDoc.EventLocation location = event.getLocation();
		if (location == null) {
			return "";
		}
		return Stream.of(location.getName(), location.getStreet(), location.getCity(), location.getCountry())
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.joining(" "));
	}

	/** The event's tags without '#'. */
	public static List<String> tagsOf(EventDoc event) {
		if (event.getTags() == null) {
			return Collections.emptyList();
		}
		List<String> tags = new ArrayList<>();
		for (String tag : event.getTags()) {
			if (tag != null) {
				tags.add(tag.replace("#", ""));
			}
		}
		return tags;
	}
}
