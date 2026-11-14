package io.github.outbox.order.domain;

/**
 * Lifecycle of an order: every order starts {@link #CREATED}; {@link #CANCELLED} is final.
 */
public enum OrderStatus {
    CREATED,
    CANCELLED
}
