package com.planmate.discovery;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads discovery.events and applies each message (DiscoveryEventImporter). Off unless
 * discovery.consumer.enabled=true (DISCOVERY_CONSUMER_ENABLED), so local runs and tests need no Redpanda.
 * Errors are handled by DiscoveryKafkaConfig: retried, then sent to discovery.events.dlq.
 */
@Component
@ConditionalOnProperty(name = "discovery.consumer.enabled", havingValue = "true")
public class DiscoveryEventListener {

	private final DiscoveryEventImporter importer;
	private final ObjectMapper json;

	public DiscoveryEventListener(DiscoveryEventImporter importer, ObjectMapper json) {
		this.importer = importer;
		this.json = json;
	}

	@KafkaListener(topics = "${discovery.topics.events:discovery.events}", groupId = "${discovery.consumer.group:planmate-backend}")
	public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
		if (record.value() == null) {
			return; // a tombstone: nothing to apply
		}
		DiscoveryMessage message = json.readValue(record.value(), DiscoveryMessage.class);
		if (record.key() != null && !record.key().equals(message.getEventId())) {
			throw new InvalidDiscoveryMessageException("The key " + record.key() + " does not match " + message.getEventId());
		}
		importer.apply(message);
	}
}
