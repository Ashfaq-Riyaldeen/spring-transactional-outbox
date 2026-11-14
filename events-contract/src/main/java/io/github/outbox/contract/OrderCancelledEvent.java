package io.github.outbox.contract;

import java.time.Instant;
import java.util.Objects;

/**
 * An order was cancelled. Published under {@link OrderEventTypes#ORDER_CANCELLED}.
 *
 * @param orderId       id of the cancelled order
 * @param customerId    customer who placed the order
 * @param occurredAt    the moment the cancellation was stored
 * @param correlationId correlation id of the request that cancelled the order
 * @param traceParent   W3C {@code traceparent} of that request, or {@code null} when it was not traced
 */
public record OrderCancelledEvent(long orderId,
                                  long customerId,
                                  Instant occurredAt,
                                  String correlationId,
                                  String traceParent) implements OrderEvent {

    public OrderCancelledEvent {
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
    }
}
