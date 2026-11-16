package io.github.outbox.order.domain;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderUnitTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:15:30Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-11-14T11:00:00Z");
    private static final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    @DisplayName("UT createdEvent() when the order has items should carry them as lines with the total")
    void createdEvent_whenOrderHasItems_shouldCarryThemAsLinesWithTheTotal() {
        // given
        Order tested = new Order(7L, 42L, OrderStatus.CREATED, new BigDecimal("24.00"), CREATED_AT, null, List.of(
                new OrderItem("sku-1", 2, new BigDecimal("10.50")),
                new OrderItem("sku-2", 1, new BigDecimal("3.00"))
        ));

        // when
        OrderCreatedEvent event = tested.createdEvent("c-1", TRACE_PARENT);

        // then
        assertThat(event).isEqualTo(new OrderCreatedEvent(
                7L,
                42L,
                List.of(new OrderLine("sku-1", 2, new BigDecimal("10.50")), new OrderLine("sku-2", 1, new BigDecimal("3.00"))),
                new BigDecimal("24.00"),
                CREATED_AT,
                "c-1",
                TRACE_PARENT
        ));
    }

    @Test
    @DisplayName("UT cancelledEvent() when the order is cancelled should carry the cancellation time")
    void cancelledEvent_whenOrderIsCancelled_shouldCarryTheCancellationTime() {
        // given
        Order tested = order(OrderStatus.CANCELLED, CANCELLED_AT);

        // when
        OrderCancelledEvent event = tested.cancelledEvent("c-1", TRACE_PARENT);

        // then
        assertThat(event).isEqualTo(new OrderCancelledEvent(7L, 42L, CANCELLED_AT, "c-1", TRACE_PARENT));
    }

    @Test
    @DisplayName("UT cancelledEvent() when the order is not cancelled should throw IllegalStateException")
    void cancelledEvent_whenOrderIsNotCancelled_shouldThrowIllegalStateException() {
        // given
        Order tested = order(OrderStatus.CREATED, null);

        // when / then
        assertThatThrownBy(() -> tested.cancelledEvent("c-1", TRACE_PARENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order 7 is CREATED, not cancelled");
    }

    private static Order order(OrderStatus status, Instant cancelledAt) {
        return new Order(7L, 42L, status, BigDecimal.ONE, CREATED_AT, cancelledAt,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
    }
}
