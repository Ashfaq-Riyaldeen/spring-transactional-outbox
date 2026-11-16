package io.github.outbox.order.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-14T10:15:30.123456Z");

    @Mock
    private OrderRepository repository;

    private OrderCancellation tested;

    @BeforeEach
    void setUp() {
        tested = new OrderCancellation(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("UT cancel() when the order is created should return it as it is after the cancellation")
    void cancel_whenOrderIsCreated_shouldReturnItAsItIsAfterTheCancellation() {
        // given
        Order cancelled = new Order(7L, 42L, OrderStatus.CANCELLED, BigDecimal.ONE, NOW_IN_MICROS, NOW_IN_MICROS,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
        when(repository.cancel(7L, NOW_IN_MICROS)).thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(cancelled));

        // when
        Order order = tested.cancel(7L, "c-1", null);

        // then
        assertThat(order).isEqualTo(cancelled);
    }

    @Test
    @DisplayName("UT cancel() when the order is already cancelled should throw OrderAlreadyCancelledException")
    void cancel_whenOrderIsAlreadyCancelled_shouldThrowOrderAlreadyCancelledException() {
        // given
        when(repository.cancel(7L, NOW_IN_MICROS)).thenReturn(false);
        when(repository.exists(7L)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> tested.cancel(7L, "c-1", null))
                .isInstanceOf(OrderAlreadyCancelledException.class)
                .hasMessage("Order 7 is already cancelled");
    }

    @Test
    @DisplayName("UT cancel() when the order does not exist should throw OrderNotFoundException")
    void cancel_whenOrderDoesNotExist_shouldThrowOrderNotFoundException() {
        // given
        when(repository.cancel(7L, NOW_IN_MICROS)).thenReturn(false);
        when(repository.exists(7L)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> tested.cancel(7L, "c-1", null))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order 7 not found");
    }
}
