package io.github.outbox.order.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle of an order: every order starts {@link #CREATED}; {@link #CANCELLED} is final.
 */
@Schema(description = "Order status: CREATED - placed and active, CANCELLED - cancelled, final", enumAsRef = true)
public enum OrderStatus {
    CREATED,
    CANCELLED
}
