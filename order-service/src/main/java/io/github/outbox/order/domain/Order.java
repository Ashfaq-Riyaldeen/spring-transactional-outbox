package io.github.outbox.order.domain;

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
}
