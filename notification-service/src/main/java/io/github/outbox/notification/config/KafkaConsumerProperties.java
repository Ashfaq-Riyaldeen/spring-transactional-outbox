package io.github.outbox.notification.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How the order events topic is consumed.
 *
 * @param orderEventsTopic topic the order events are read from
 * @param retries          how many times a failed event is retried before it goes to the dead letter topic
 * @param retryInterval    pause between two attempts
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaConsumerProperties(@NotBlank String orderEventsTopic,
                                      @PositiveOrZero long retries,
                                      @NotNull Duration retryInterval) {

    public String deadLetterTopic() {
        return orderEventsTopic + "-dlt";
    }
}
