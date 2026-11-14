package io.github.outbox.contract;

/**
 * Event types under which order events are published and consumed.
 * <p>
 * The same strings key the publisher's outbox configuration and the consumer's mapping from event type to
 * payload class, so both services take them from here.
 */
public final class OrderEventTypes {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";

    private OrderEventTypes() {
    }
}
