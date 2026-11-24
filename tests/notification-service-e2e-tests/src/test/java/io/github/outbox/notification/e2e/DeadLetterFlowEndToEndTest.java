package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEventTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterFlowEndToEndTest extends NotificationServiceEndToEndTestBase {

    private final DeadLetterProbe deadLetters =
            new DeadLetterProbe(NotificationEndToEndContainers.KAFKA.getBootstrapServers(), DEAD_LETTER_TOPIC);

    DeadLetterFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @AfterEach
    void closeProbe() {
        deadLetters.close();
    }

    @Test
    @DisplayName("E2E dead letter when the event type is unknown should move the record aside without retrying it")
    void deadLetter_whenEventTypeIsUnknown_shouldMoveTheRecordAsideWithoutRetryingIt() {
        // given
        String marker = "unknown-type-" + UUID.randomUUID();

        // when
        orderService.publishRaw(UUID.randomUUID().toString(), "order-shipped", "com.example.OrderShippedEvent",
                "{\"marker\": \"%s\"}".formatted(marker));

        // then
        Waits.awaitDelivery().until(() -> deadLetters.containing(marker).size() == 1);
        assertThat(deadLetters.delaysOf(marker)).singleElement()
                .satisfies(delay -> assertThat(delay).isLessThan(Waits.RETRY_INTERVAL));
    }

    @Test
    @DisplayName("E2E dead letter when the event id header is missing should move the record aside at once")
    void deadLetter_whenEventIdHeaderIsMissing_shouldMoveTheRecordAsideAtOnceAndNotifyNobody() {
        // given
        long orderId = newOrderId();
        String correlationId = "missing-id-" + UUID.randomUUID();

        // when
        orderService.publish(null, OrderEventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), correlationId, null));

        // then
        Waits.awaitDelivery().until(() -> deadLetters.containing(correlationId).size() == 1);
        assertThat(deadLetters.delaysOf(correlationId)).singleElement()
                .satisfies(delay -> assertThat(delay).isLessThan(Waits.RETRY_INTERVAL));
        assertThat(api.findByOrderId(orderId).json()).isEmpty();
    }

    @Test
    @DisplayName("E2E dead letter when a bad record was moved aside should keep consuming the events after it")
    void deadLetter_whenBadRecordWasMovedAside_shouldKeepConsumingTheEventsAfterIt() {
        // given
        long orderId = newOrderId();
        String marker = "before-good-" + UUID.randomUUID();
        orderService.publishRaw(UUID.randomUUID().toString(), "order-shipped", "com.example.OrderShippedEvent",
                "{\"marker\": \"%s\"}".formatted(marker));

        // when
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), "c-1", null));

        // then
        Waits.awaitDelivery().until(() -> api.findByOrderId(orderId).json().size() == 1);
        // without a key the two records may sit on different partitions, so the good one can be handled first
        Waits.awaitDelivery().until(() -> deadLetters.containing(marker).size() == 1);
    }
}
