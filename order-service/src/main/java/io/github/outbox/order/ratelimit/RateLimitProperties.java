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
 * @param global         the limit shared by every caller of the service
 * @param perIp          the limit of one client address
 * @param perCustomer    the limit of one customer, whatever address the orders come from
 * @param maxTrackedKeys how many addresses and how many customers are tracked at most; the ones idle longest are
 *                       dropped first, and a dropped one starts again with a full allowance
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit.order-creation")
public record RateLimitProperties(@NotNull @Valid Limit global,
                                  @NotNull @Valid Limit perIp,
                                  @NotNull @Valid Limit perCustomer,
                                  @Positive long maxTrackedKeys) {

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
