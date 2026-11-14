package io.github.outbox.order.domain;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(long orderId) {
        super("Order %d is already cancelled".formatted(orderId));
    }
}
