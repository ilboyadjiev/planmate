package com.planmate.controller2;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;
import com.planmate.service.EventPreviewService;

/**
 * Link previews for shared events: nginx sends the crawlers of Facebook, Messenger, Instagram, WhatsApp and other
 * apps here when they fetch /events/{id} (see EventPreviewService). Open to everyone, like a public event's page.
 */
@RestController
public class EventPreviewController {

	private final EventMongoRepository repository;
	private final EventPreviewService previews;

	public EventPreviewController(EventMongoRepository repository, EventPreviewService previews) {
		this.repository = repository;
		this.previews = previews;
	}

	/** Always 200, also for unknown events: a broken preview would show the link with no card at all. */
	@GetMapping(value = "/api/v2/events/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> preview(@PathVariable String id) {
		EventDoc event = id.length() > 64 ? null : repository.findById(id).orElse(null);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
				.body(previews.page(id, event));
	}
}
