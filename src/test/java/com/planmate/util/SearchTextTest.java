package com.planmate.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.planmate.dto2.EventDoc;

class SearchTextTest {

	@Test
	void tokensAreLowerCaseWithoutAccentsOrFillerWords() {
		assertEquals(Arrays.asList("jazz", "cafe", "munchen", "2026"),
				SearchText.tokens("Jazz im Café München, 2026!"));
		assertEquals(Arrays.asList("strasse", "rock"), SearchText.tokens("Straße & the a Rock"));
	}

	@Test
	void queryTokensAreDistinctAndLimited() {
		assertEquals(Arrays.asList("jazz", "night"), SearchText.queryTokens("jazz JAZZ night", 8));
		assertEquals(2, SearchText.queryTokens("one two three", 2).size());
		assertTrue(SearchText.queryTokens("  ", 8).isEmpty());
	}

	@Test
	void stemRemovesOneCommonEndingButKeepsShortWords() {
		assertEquals("concert", SearchText.stem("concerts"));
		assertEquals("konzert", SearchText.stem("konzerte"));
		assertEquals("game", SearchText.stem("games"));
		assertEquals("beer", SearchText.stem("beer"));
		assertEquals("bar", SearchText.stem("bar"));
	}

	@Test
	void eventTermsComeFromTitleTagsLocationAndDescription() {
		EventDoc event = new EventDoc();
		event.setTitle("Jazz Night");
		event.setTags(Arrays.asList("#munich", "#live"));
		EventDoc.EventLocation location = new EventDoc.EventLocation();
		location.setName("Unterfahrt");
		location.setCity("München");
		event.setLocation(location);
		event.setDescription("Live jazz with friends");

		List<String> terms = SearchText.terms(event);
		assertEquals(Arrays.asList("jazz", "night", "munich", "live", "unterfahrt", "munchen", "friends"), terms);
	}
}
