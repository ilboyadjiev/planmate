package com.planmate.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.planmate.dto2.EventDoc;

/**
 * Works out which city an event is in, as a hashtag like "#munich".
 *
 * Sources, in order of trust:
 * 1. the location's structured city (picked from LocationIQ suggestions, so certainly a city),
 * 2. a known city mentioned in the location text, 3. in the title, 4. in the description.
 * "Known" means listed in cities.txt with its spellings (München, Muenchen, Muc all become #munich).
 * Free text only matches whole words, ignoring case and accents.
 */
public final class CityTags {

	/** Normalised spelling -> name the tag is made from. Longest spellings first, so "frankfurt am main" wins. */
	private static final Map<String, String> SPELLINGS = load();
	private static final Pattern KNOWN_CITY = Pattern.compile("(?<![\\p{L}\\p{N}])("
			+ SPELLINGS.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|"))
			+ ")(?![\\p{L}\\p{N}])");

	private CityTags() {
	}

	/** The city tag for an event, if its location, title or description names a city. */
	public static Optional<String> forEvent(EventDoc event) {
		EventDoc.EventLocation location = event.getLocation();
		if (location != null && StringUtils.isNotBlank(location.getCity())) {
			return Optional.of(forCity(location.getCity()));
		}
		return Stream.of(location == null ? null : location.getName(), event.getTitle(), event.getDescription())
				.map(CityTags::knownCityIn)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.findFirst()
				.map(CityTags::toTag);
	}

	/** The tag for a city name: known spellings map to one tag ("München" and "Munich" give "#munich"). */
	public static String forCity(String city) {
		String normalised = normalise(city);
		return toTag(SPELLINGS.getOrDefault(normalised, city));
	}

	private static Optional<String> knownCityIn(String text) {
		if (StringUtils.isBlank(text)) {
			return Optional.empty();
		}
		Matcher matcher = KNOWN_CITY.matcher(normalise(text));
		return matcher.find() ? Optional.of(SPELLINGS.get(matcher.group(1))) : Optional.empty();
	}

	/** "New York" -> "#newyork", "Düsseldorf" -> "#dusseldorf", "Garmisch-Partenkirchen" -> "#garmischpartenkirchen". */
	private static String toTag(String city) {
		return "#" + normalise(city).replaceAll("[^\\p{L}\\p{N}]", "");
	}

	/** Lower case, accents removed (ü -> u, ß -> ss), whitespace collapsed. */
	static String normalise(String text) {
		String lower = StringUtils.normalizeSpace(text).toLowerCase().replace("ß", "ss");
		return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private static Map<String, String> load() {
		try (InputStream in = CityTags.class.getResourceAsStream("/cities.txt")) {
			if (in == null) {
				throw new IllegalStateException("cities.txt is missing from the classpath");
			}
			List<String[]> entries = new ArrayList<>();
			new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.forEach(line -> {
						String[] names = line.split("\\|");
						for (String name : names) {
							entries.add(new String[] { normalise(name), names[0].trim() });
						}
					});
			entries.sort(Comparator.comparingInt((String[] e) -> e[0].length()).reversed());
			Map<String, String> spellings = new LinkedHashMap<>();
			entries.forEach(e -> spellings.putIfAbsent(e[0], e[1]));
			return spellings;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
