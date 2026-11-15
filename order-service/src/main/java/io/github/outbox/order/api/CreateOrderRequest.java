package io.github.outbox.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Order to place")
public record CreateOrderRequest(

        @Schema(
                description = "Id of the customer placing the order",
                example = "1001",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "customerId must not be null")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @Schema(
                description = "Items of the order",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotEmpty(message = "items must not be empty")
        @Size(max = 50, message = "items must contain at most {max} entries")
        List<@NotNull(message = "items must not contain null entries") @Valid OrderItemRequest> items
) {
}
