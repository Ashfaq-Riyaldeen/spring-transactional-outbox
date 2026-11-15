package io.github.outbox.order.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;

/**
 * Lets an order creation through while the rate limits allow it, a token bucket per limit.
 */
public class OrderCreationRateLimiter {

    private final Bucket global;

    public OrderCreationRateLimiter(RateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_MILLISECONDS);
    }

    OrderCreationRateLimiter(RateLimitProperties properties, TimeMeter timeMeter) {
        this.global = bucket(properties.global(), timeMeter);
    }

    /**
     * Takes one request from the global limit.
     *
     * @throws RateLimitExceededException when the limit is used up; it carries the wait until the next request fits
     */
    public void acquire() {
        ConsumptionProbe probe = global.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException("Too many orders are being created, retry later",
                    retryAfter(probe.getNanosToWaitForRefill()));
        }
    }

    private static Bucket bucket(RateLimitProperties.Limit limit, TimeMeter timeMeter) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    // Retry-After is in whole seconds; a wait shorter than a second is rounded up rather than sent as zero.
    private static Duration retryAfter(long nanos) {
        long seconds = Math.max(1, Duration.ofNanos(nanos).plusNanos(999_999_999).toSeconds());
        return Duration.ofSeconds(seconds);
    }
}
