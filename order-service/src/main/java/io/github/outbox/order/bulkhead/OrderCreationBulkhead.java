package io.github.outbox.order.bulkhead;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the number of order creations running at once under a limit, so that a burst waits at the door instead of
 * queueing for database connections inside the service. Every {@link #acquire()} that returns has to be matched by
 * one {@link #release()}.
 */
public class OrderCreationBulkhead {

    private static final Duration RETRY_AFTER = Duration.ofSeconds(1);

    private final Semaphore slots;
    private final Duration maxWait;

    public OrderCreationBulkhead(BulkheadProperties properties) {
        this.slots = new Semaphore(properties.maxConcurrent(), true);
        this.maxWait = properties.maxWait();
    }

    /**
     * Takes a slot, waiting up to the configured time for one to be released.
     *
     * @throws BulkheadFullException when no slot became free in time, or the waiting thread was interrupted
     */
    public void acquire() {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw new BulkheadFullException("Too many orders are being created at the same time, retry later",
                    RETRY_AFTER);
        }
    }

    public void release() {
        slots.release();
    }

    /**
     * @return how many slots are free right now
     */
    public int availableSlots() {
        return slots.availablePermits();
    }
}
