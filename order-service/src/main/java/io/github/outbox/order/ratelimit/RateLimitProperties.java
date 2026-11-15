package io.github.outbox.order.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits on how fast orders can be created.
 *
 * @param global the limit shared by every caller of the service
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit.order-creation")
public record RateLimitProperties(@NotNull @Valid Limit global) {

    /**
     * At most {@code capacity} requests per {@code period}; the allowance refills gradually over the period, so a
     * burst of {@code capacity} requests is accepted at once and the next one waits for its share.
     *
     * @param capacity how many requests fit into one period
     * @param period   the period the capacity refills over
     */
    public record Limit(@Positive long capacity, @NotNull Duration period) {
    }
}
