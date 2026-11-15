package io.github.outbox.order.bulkhead;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How many orders can be created at the same time.
 *
 * @param maxConcurrent how many creations run at once
 * @param maxWait       how long a request waits for one of them to finish before it is refused; zero refuses at once
 */
@Validated
@ConfigurationProperties(prefix = "app.bulkhead.order-creation")
public record BulkheadProperties(@Positive int maxConcurrent, @NotNull Duration maxWait) {
}
