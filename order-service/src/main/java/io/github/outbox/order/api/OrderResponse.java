package io.github.outbox.order.api;

import io.github.outbox.order.domain.Order;
import io.github.outbox.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Order")
public record OrderResponse(

        @Schema(description = "Id of the order", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(
                description = "Id of the customer who placed the order",
                example = "1001",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long customerId,

        @Schema(description = "Current status of the order", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(
                description = "Sum of the subtotals of all items",
                example = "21.00",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        BigDecimal total,

        @Schema(
                description = "Moment the order was stored",
                example = "2026-11-14T10:15:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant createdAt,

        @Schema(
                description = "Moment the order was cancelled, null while it is not",
                example = "2026-11-14T10:20:00Z",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant cancelledAt,

        @Schema(description = "Ordered items", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderItemResponse> items
) {

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
