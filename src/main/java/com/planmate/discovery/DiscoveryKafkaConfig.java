package com.planmate.discovery;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * What happens when a discovery message cannot be applied: temporary problems (the database is briefly away) are
 * retried twice a second apart; then, and right away for messages that can never work (not JSON, missing fields),
 * the message goes to discovery.events.dlq with the reason in its headers, so nothing is lost silently and the
 * messages after it are not held up.
 */
@Configuration
@ConditionalOnProperty(name = "discovery.consumer.enabled", havingValue = "true")
public class DiscoveryKafkaConfig {

	@Bean
	CommonErrorHandler discoveryErrorHandler(KafkaTemplate<Object, Object> template,
			@Value("${discovery.topics.dead-letter:discovery.events.dlq}") String deadLetterTopic) {
		DeadLetterPublishingRecoverer toDeadLetters = new DeadLetterPublishingRecoverer(template,
				(record, error) -> new TopicPartition(deadLetterTopic, -1));
		DefaultErrorHandler handler = new DefaultErrorHandler(toDeadLetters, new FixedBackOff(1000L, 2));
		handler.addNotRetryableExceptions(InvalidDiscoveryMessageException.class, JsonProcessingException.class);
		return handler;
	}
}
