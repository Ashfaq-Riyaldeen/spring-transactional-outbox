package io.github.outbox.loadtests;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Customer ids {@code 1..size} handed out in turn, safe to call from every virtual user at once.
 */
final class CustomerIdPool {

    private final int size;
    private final AtomicLong sequence = new AtomicLong();

    CustomerIdPool(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("customerPoolSize must be at least 1, was %d".formatted(size));
        }
        this.size = size;
    }

    long next() {
        return Math.floorMod(sequence.getAndIncrement(), size) + 1;
    }
}
