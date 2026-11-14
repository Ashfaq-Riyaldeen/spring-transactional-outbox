package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEventTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DatabaseOutageFlowEndToEndTest extends NotificationServiceEndToEndTestBase {

    // Longer than the fixed retries (3 x 200 ms plus a connection timeout each) would take to give up.
    private static final Duration OUTAGE = Duration.ofSeconds(12);

    private final DatabaseOutage outage = new DatabaseOutage();
    private final DeadLetterProbe deadLetters =
            new DeadLetterProbe(NotificationEndToEndContainers.KAFKA.getBootstrapServers(), DEAD_LETTER_TOPIC);

    DatabaseOutageFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @AfterEach
    void closeProbe() {
        deadLetters.close();
    }

    @Test
    @DisplayName("E2E database outage when an event arrives while the database is away should notify once it is back")
    void outage_whenEventArrivesWhileDatabaseIsAway_shouldNotifyOnceItIsBack() {
        // given
        long orderId = newOrderId();
        String correlationId = "db-outage-" + UUID.randomUUID();
        outage.begin();
        try {
            // when
            orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CANCELLED,
                    new OrderCancelledEvent(orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), correlationId, null));

            // then - the event waits for the database instead of going to the dead letter topic
            await().during(OUTAGE).atMost(OUTAGE.plusSeconds(5)).pollInSameThread()
                    .until(() -> deadLetters.containing(correlationId).isEmpty());
        } finally {
            outage.end();
        }
        Waits.awaitDelivery().until(() -> api.findByOrderId(orderId).json().size() == 1);
        assertThat(deadLetters.containing(correlationId)).isEmpty();
    }
}
