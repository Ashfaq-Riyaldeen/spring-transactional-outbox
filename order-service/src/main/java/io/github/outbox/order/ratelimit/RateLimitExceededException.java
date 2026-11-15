package io.github.outbox.order.ratelimit;

import java.time.Duration;

/**
 * A request was refused because a rate limit is used up. {@code retryAfter} is how long the caller has to wait
 * before the limit lets the next request through.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
