package io.github.outbox.order.api;

import io.github.outbox.order.domain.Order;
import io.github.outbox.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An order as the API returns it.
 *
 * @param id          id of the order
 * @param customerId  customer who placed the order
 * @param status      current status
 * @param total       sum of the subtotals of all items
 * @param createdAt   the moment the order was stored
 * @param cancelledAt the moment the order was cancelled, {@code null} while it is not
 * @param items       the ordered items
 */
public record OrderResponse(long id,
                            long customerId,
                            OrderStatus status,
                            BigDecimal total,
                            Instant createdAt,
                            Instant cancelledAt,
                            List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.status(),
                order.total(),
                order.createdAt(),
                order.cancelledAt(),
                order.items().stream().map(OrderItemResponse::from).toList()
        );
    }
}
