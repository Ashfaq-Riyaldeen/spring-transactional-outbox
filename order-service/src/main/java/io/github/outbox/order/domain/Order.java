package io.github.outbox.order.domain;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An order as it is stored.
 *
 * @param id          id of the order
 * @param customerId  customer who placed the order
 * @param status      current status
 * @param total       sum of the subtotals of all items
 * @param createdAt   the moment the order was stored
 * @param cancelledAt the moment the order was cancelled, {@code null} while it is not
 * @param items       the ordered items, never empty
 */
public record Order(long id,
                    long customerId,
                    OrderStatus status,
                    BigDecimal total,
                    Instant createdAt,
                    Instant cancelledAt,
                    List<OrderItem> items) {

    public Order {
        items = List.copyOf(items);
    }

    public OrderCreatedEvent createdEvent(String correlationId) {
        List<OrderLine> lines = items.stream()
                .map(item -> new OrderLine(item.productId(), item.quantity(), item.price()))
                .toList();
        return new OrderCreatedEvent(id, customerId, lines, total, createdAt, correlationId, null);
    }

    /**
     * @throws IllegalStateException if the order is not cancelled
     */
    public OrderCancelledEvent cancelledEvent(String correlationId) {
        if (status != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order %d is %s, not cancelled".formatted(id, status));
        }
        return new OrderCancelledEvent(id, customerId, cancelledAt, correlationId, null);
    }
}
