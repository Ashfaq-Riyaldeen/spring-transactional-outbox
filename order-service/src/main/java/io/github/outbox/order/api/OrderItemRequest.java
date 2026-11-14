package io.github.outbox.order.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderItemRequest(

        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId must be at most {max} characters")
        String productId,

        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        @Max(value = 1000, message = "quantity must be at most {value}")
        Integer quantity,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        @Digits(integer = 17, fraction = 2, message = "price must have at most {integer} integer and {fraction} fraction digits")
        BigDecimal price
) {
}
