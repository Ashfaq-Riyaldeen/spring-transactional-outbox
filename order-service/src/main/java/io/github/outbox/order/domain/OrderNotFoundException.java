package io.github.outbox.order.domain;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long orderId) {
        super("Order %d not found".formatted(orderId));
    }
}
