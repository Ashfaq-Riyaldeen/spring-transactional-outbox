package io.github.outbox.order.bulkhead;

import java.time.Duration;

/**
 * A request was refused because every slot of a bulkhead stayed taken for as long as it could wait.
 * {@code retryAfter} is a hint for the caller, not a promise that a slot will be free by then.
 */
public class BulkheadFullException extends RuntimeException {

    private final Duration retryAfter;

    public BulkheadFullException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
