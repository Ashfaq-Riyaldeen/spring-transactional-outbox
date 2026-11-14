package io.github.outbox.order.domain;

import java.math.BigDecimal;

public record OrderItem(String productId, int quantity, BigDecimal price) {

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
