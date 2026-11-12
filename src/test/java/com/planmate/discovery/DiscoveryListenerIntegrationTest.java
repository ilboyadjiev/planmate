package com.planmate.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.PlanmateApplication;
import com.planmate.dao.EventMongoRepository;
import com.planmate.dto2.EventDoc;

/**
 * The listener with a real (embedded) Kafka broker and the real MongoDB: good messages become events, broken ones
 * end up in discovery.events.dlq instead of disappearing or blocking the messages after them.
 */
@SpringBootTest(classes = PlanmateApplication.class, properties = {
		"discovery.consumer.enabled=true",
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}" })
@EmbeddedKafka(partitions = 1, topics = { "discovery.events", "discovery.events.dlq" })
@DirtiesContext
class DiscoveryListenerIntegrationTest {

	private static final String ID = "tm-IT" + System.nanoTime();

	@Autowired
	private KafkaTemplate<String, String> kafka;
	@Autowired
	private EventMongoRepository events;
	@Autowired
	private ObjectMapper json;
	@Autowired
	private EmbeddedKafkaBroker broker;

	@AfterEach
	void cleanUp() {
		events.deleteById(ID);
	}

	private EventDoc waitForEvent(long version) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			EventDoc doc = events.findById(ID).orElse(null);
			if (doc != null && doc.getDiscoveryVersion() != null && doc.getDiscoveryVersion() >= version) {
				return doc;
			}
			Thread.sleep(200);
		}
		return null;
	}

	@Test
	void messagesBecomeEventsAndBrokenOnesGoToTheDeadLetterTopic() throws Exception {
		DiscoveryMessage message = DiscoveryEventImporterTest.message(DiscoveryMessage.DISCOVERED, 1);
		message.setEventId(ID);
		message.getEvent().setId(ID);
		kafka.send("discovery.events", "garbage", "{not json").get();
		kafka.send("discovery.events", ID, json.writeValueAsString(message)).get();

		EventDoc doc = waitForEvent(1);
		assertNotNull(doc, "the good message after the broken one is applied");
		assertEquals("Jazz Night", doc.getTitle());
		assertTrue(doc.getDescription().endsWith("Source: Ticketmaster"));

		Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-reader", "false", broker);
		props.put("auto.offset.reset", "earliest");
		try (Consumer<String, String> dlq = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
				new StringDeserializer()).createConsumer()) {
			broker.consumeFromAnEmbeddedTopic(dlq, "discovery.events.dlq");
			ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(dlq, "discovery.events.dlq", Duration.ofSeconds(20));
			assertEquals("{not json", dead.value());
			assertEquals("garbage", dead.key());
			assertNotNull(dead.headers().lastHeader("kafka_dlt-exception-message"), "the reason travels with it");
		}
	}
}
