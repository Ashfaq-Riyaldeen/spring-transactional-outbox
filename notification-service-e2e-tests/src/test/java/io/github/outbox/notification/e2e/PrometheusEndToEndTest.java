package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.contract.OrderLine;
import io.github.outbox.notification.e2e.NotificationApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndToEndTest extends NotificationServiceEndToEndTestBase {

    PrometheusEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E GET /actuator/prometheus when an event was consumed should expose the inbox metrics")
    void prometheus_whenEventWasConsumed_shouldExposeTheInboxMetrics() {
        // given
        long orderId = newOrderId();
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CREATED, new OrderCreatedEvent(orderId, 42L,
                List.of(new OrderLine("sku-1", 1, new BigDecimal("10.50"))), new BigDecimal("10.50"),
                Instant.parse("2026-11-14T10:15:30Z"), "c-1", null));

        // when / then
        Waits.awaitDelivery().untilAsserted(() -> {
            Response metrics = api.metrics();
            assertThat(metrics.status()).as(metrics.body()).isEqualTo(200);
            assertThat(metrics.body()).as("the scrape")
                    .containsPattern("(?m)^inbox_events_total\\{[^}]*application=\"notification-service\"[^}]*} [1-9]");
        });
    }
}
