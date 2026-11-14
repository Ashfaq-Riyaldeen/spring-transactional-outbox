package io.github.outbox.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An order was placed. Published under {@link OrderEventTypes#ORDER_CREATED}.
 *
 * @param orderId       id of the order
 * @param customerId    customer who placed the order
 * @param lines         the ordered lines, never empty; an unmodifiable copy of what was passed
 * @param total         sum of quantity times price over all lines
 * @param occurredAt    the moment the order was stored
 * @param correlationId correlation id of the request that placed the order
 * @param traceParent   W3C {@code traceparent} of that request, or {@code null} when it was not traced
 */
public record OrderCreatedEvent(long orderId,
                                long customerId,
                                List<OrderLine> lines,
                                BigDecimal total,
                                Instant occurredAt,
                                String correlationId,
                                String traceParent) implements OrderEvent {

    public OrderCreatedEvent {
        Objects.requireNonNull(lines, "lines cannot be null");
        Objects.requireNonNull(total, "total cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines cannot be empty");
        }
        lines = List.copyOf(lines);
    }
}
