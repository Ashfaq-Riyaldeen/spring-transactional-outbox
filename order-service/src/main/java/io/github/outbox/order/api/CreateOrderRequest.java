package io.github.outbox.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @NotNull(message = "customerId must not be null")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @NotEmpty(message = "items must not be empty")
        @Size(max = 50, message = "items must contain at most {max} entries")
        List<@NotNull(message = "items must not contain null entries") @Valid OrderItemRequest> items
) {
}
