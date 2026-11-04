package com.planmate.config;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

import com.planmate.dto2.EventDoc;
import com.planmate.util.SearchText;

/**
 * Keeps each event's search words (EventDoc.searchTerms) in step with its text: recomputed whenever an event is
 * saved, however it is saved. Partial updates (joining, answering) do not touch the text, so they need nothing.
 */
@Component
public class SearchTermsListener extends AbstractMongoEventListener<EventDoc> {

	@Override
	public void onBeforeConvert(BeforeConvertEvent<EventDoc> event) {
		EventDoc doc = event.getSource();
		doc.setSearchTerms(SearchText.terms(doc));
	}
}
