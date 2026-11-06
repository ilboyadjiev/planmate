package com.planmate.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.planmate.dto2.EventDoc;

class CityTagsTest {

	private static Optional<String> tagFor(String city, String locationName, String title, String description) {
		EventDoc event = new EventDoc();
		if (city != null || locationName != null) {
			EventDoc.EventLocation location = new EventDoc.EventLocation();
			location.setCity(city);
			location.setName(locationName);
			event.setLocation(location);
		}
		event.setTitle(title);
		event.setDescription(description);
		return CityTags.forEvent(event);
	}

	@ParameterizedTest(name = "{4}")
	@CsvSource(delimiter = ';', nullValues = "-", value = {
			// structured city, location text, title, description; expected; why
			"München; Werksviertel; Party; -; #munich; structured city, local spelling",
			"Unterföhring; -; Party; -; #unterfohring; structured city that is not in the list",
			"-; Café Spielwiese, Munich; Dixit; -; #munich; city in the location text",
			"-; Café Spielwiese; Dixit night in Köln; -; #cologne; city in the title, German spelling",
			"-; -; Board games; Meet at Muenchen Hbf; #munich; city in the description, transliterated",
			"-; Berlin Mitte; Trip to Munich; -; #berlin; location text beats the title",
			"-; -; Weekend in Frankfurt am Main; -; #frankfurt; longest spelling wins",
			"-; -; Flight to New York; -; #newyork; two-word city",
			"-; -; Munichsburg festival; -; -; part of a word is not a city",
			"-; -; Essen und Trinken; -; -; everyday words are not cities",
			"-; -; Board games; -; -; nothing to find",
			"София; НДК; Концерт; -; #sofia; Bulgarian venues write the city in Cyrillic",
			"-; Joy Station; Концерт в София; -; #sofia; Cyrillic city in the title" })
	void findsTheCity(String city, String locationName, String title, String description, String expected,
			String why) {
		assertEquals(Optional.ofNullable(expected), tagFor(city, locationName, title, description), why);
	}

	@ParameterizedTest
	@CsvSource({ "Munich,#munich", "München,#munich", "muenchen,#munich", "New York,#newyork", "Wien,#vienna", "Sofia,#sofia", "София,#sofia",
			"Somewhere Else,#somewhereelse" })
	void profileCitiesUseTheSameTags(String city, String expected) {
		assertEquals(expected, CityTags.forCity(city));
	}
}
