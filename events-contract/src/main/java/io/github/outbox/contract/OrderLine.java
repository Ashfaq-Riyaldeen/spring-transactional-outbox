package io.github.outbox.contract;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of an order as it is carried by {@link OrderCreatedEvent}: a product, how many and at which unit price.
 */
public record OrderLine(String productId, int quantity, BigDecimal price) {

    public OrderLine {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
    }
}
