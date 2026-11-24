package io.github.outbox.order.e2e;

import io.github.outbox.order.e2e.OrderApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndToEndTest extends OrderServiceEndToEndTestBase {

    PrometheusEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E GET /actuator/prometheus when an order was created should expose it tagged with the service")
    void prometheus_whenOrderWasCreated_shouldExposeItsRequestTaggedWithTheService() {
        // given
        Response created = api.create(orderBody(newCustomerId()), null);
        assertThat(created.status()).as(created.body()).isEqualTo(201);

        // when
        Response metrics = api.metrics();

        // then
        assertThat(metrics.status()).as(metrics.body()).isEqualTo(200);
        assertThat(metrics.body()).as("the scrape").containsPattern(
                "http_server_requests_seconds_count\\{[^}]*application=\"order-service\"[^}]*method=\"POST\""
                        + "[^}]*status=\"201\"[^}]*uri=\"/api/v1/orders\"[^}]*} [1-9]");
        assertThat(metrics.body()).as("latency histogram buckets").contains("http_server_requests_seconds_bucket{");
        assertThat(metrics.body()).as("refusals counted per reason, zero until one happens")
                .containsPattern("orders_creation_rejected_total\\{[^}]*reason=\"global\"")
                .containsPattern("orders_creation_rejected_total\\{[^}]*reason=\"per_ip\"")
                .containsPattern("orders_creation_rejected_total\\{[^}]*reason=\"per_customer\"")
                .containsPattern("orders_creation_rejected_total\\{[^}]*reason=\"bulkhead\"");
    }

    @Test
    @DisplayName("E2E GET /actuator/prometheus when an event was published should expose the outbox metrics")
    void prometheus_whenEventWasPublished_shouldExposeTheOutboxMetrics() {
        // given
        Response created = api.create(orderBody(newCustomerId()), null);
        assertThat(created.status()).as(created.body()).isEqualTo(201);

        // when / then
        Waits.awaitDelivery().untilAsserted(() -> {
            String scrape = api.metrics().body();
            assertThat(scrape).as("events by status").containsPattern("(?m)^outbox_events_by_status\\{[^}]*status=");
            assertThat(scrape).as("polling tasks that succeeded")
                    .containsPattern("(?m)^oncebox_succeeded_tasks_total\\{[^}]*} [1-9]");
            assertThat(scrape).as("time spent processing a batch")
                    .containsPattern("(?m)^oncebox_task_processing_duration_seconds_count\\{");
        });
    }
}
