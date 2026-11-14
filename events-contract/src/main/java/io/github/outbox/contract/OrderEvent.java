package io.github.outbox.contract;

import java.time.Instant;

/**
 * An event in the life of an order, published by the order service and consumed by the notification service.
 * <p>
 * The event id is not part of the payload: it travels in the message headers and is assigned when the event is
 * written to the outbox.
 */
public sealed interface OrderEvent permits OrderCreatedEvent, OrderCancelledEvent {

    long orderId();

    long customerId();

    /**
     * @return the moment the change happened in the order service, never {@code null}.
     */
    Instant occurredAt();

    /**
     * @return the correlation id of the request that caused the event, never {@code null}.
     */
    String correlationId();

    /**
     * @return the W3C {@code traceparent} of the request that caused the event, or {@code null} when the request
     *         was not traced.
     */
    String traceParent();
}
