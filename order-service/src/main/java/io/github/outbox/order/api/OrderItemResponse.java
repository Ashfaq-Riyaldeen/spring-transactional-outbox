package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(String productId, int quantity, BigDecimal price) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.productId(), item.quantity(), item.price());
    }
}
