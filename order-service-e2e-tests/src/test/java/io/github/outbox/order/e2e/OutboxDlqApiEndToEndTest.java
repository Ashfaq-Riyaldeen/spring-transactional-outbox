package io.github.outbox.order.e2e;

import io.github.outbox.order.e2e.OrderApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDlqApiEndToEndTest extends OrderServiceEndToEndTestBase {

    OutboxDlqApiEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E GET /outbox-dlq/events/count when the service runs should answer the number of DLQ events")
    void count_whenServiceRuns_shouldAnswerTheNumberOfDlqEvents() {
        // when
        Response count = api.dlqCount();

        // then
        assertThat(count.status()).as(count.body()).isEqualTo(200);
        assertThat(count.json().isNumber()).as(count.body()).isTrue();
    }
}
