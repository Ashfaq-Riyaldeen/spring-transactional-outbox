package io.github.outbox.order.bulkhead;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreationBulkheadUnitTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("UT acquire() when slots are free should take them one by one")
    void acquire_whenSlotsAreFree_shouldTakeThemOneByOne() {
        // given
        OrderCreationBulkhead tested = bulkhead(2);

        // when
        tested.acquire();
        tested.acquire();

        // then
        assertThat(tested.availableSlots()).isZero();
    }

    @Test
    @DisplayName("UT acquire() when every slot is taken should refuse with a one second retry hint")
    void acquire_whenEverySlotIsTaken_shouldRefuseWithOneSecondRetryHint() {
        // given
        OrderCreationBulkhead tested = bulkhead(1);
        tested.acquire();

        // when / then
        assertThatThrownBy(tested::acquire)
                .isInstanceOfSatisfying(BulkheadFullException.class, exception -> {
                    assertThat(exception.getMessage())
                            .isEqualTo("Too many orders are being created at the same time, retry later");
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    @DisplayName("UT acquire() when a caller is refused should count it with the bulkhead reason")
    void acquire_whenCallerIsRefused_shouldCountItWithTheBulkheadReason() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderCreationBulkhead tested = new OrderCreationBulkhead(new BulkheadProperties(1, Duration.ZERO), registry);
        tested.acquire();

        // when
        assertThatThrownBy(tested::acquire).isInstanceOf(BulkheadFullException.class);

        // then
        assertThat(registry.get("orders.creation.rejected").tag("reason", "bulkhead").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT release() when a slot is released should let the next request take it")
    void release_whenSlotIsReleased_shouldLetNextRequestTakeIt() {
        // given
        OrderCreationBulkhead tested = bulkhead(1);
        tested.acquire();

        // when
        tested.release();

        // then
        assertThatCode(tested::acquire).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquire() when the waiting thread is interrupted should refuse and keep the interrupt flag")
    void acquire_whenWaitingThreadIsInterrupted_shouldRefuseAndKeepInterruptFlag() {
        // given
        OrderCreationBulkhead tested =
                new OrderCreationBulkhead(new BulkheadProperties(1, Duration.ofSeconds(5)), new SimpleMeterRegistry());
        tested.acquire();
        Thread.currentThread().interrupt();

        // when / then
        assertThatThrownBy(tested::acquire).isInstanceOf(BulkheadFullException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static OrderCreationBulkhead bulkhead(int maxConcurrent) {
        return new OrderCreationBulkhead(new BulkheadProperties(maxConcurrent, Duration.ZERO),
                new SimpleMeterRegistry());
    }
}
