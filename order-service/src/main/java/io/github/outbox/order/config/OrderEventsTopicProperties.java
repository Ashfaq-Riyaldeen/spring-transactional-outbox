package io.github.outbox.order.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The Kafka topic order events are published to, created on startup when it does not exist yet.
 *
 * @param name       name of the topic
 * @param partitions number of partitions it is created with
 * @param replicas   replication factor it is created with
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka.order-events")
public record OrderEventsTopicProperties(@NotBlank String name, @Positive int partitions, @Positive short replicas) {
}
