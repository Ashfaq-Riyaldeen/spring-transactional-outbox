package io.github.outbox.notification.e2e;

import io.github.outbox.notification.e2e.NotificationApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class HealthEndToEndTest extends NotificationServiceEndToEndTestBase {

    HealthEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E GET /actuator/health when Kafka answers should report the kafka component up")
    void health_whenKafkaAnswers_shouldReportKafkaComponentUp() {
        // when
        Response health = api.health("");

        // then
        assertThat(health.status()).as(health.body()).isEqualTo(200);
        assertThat(health.json().at("/components/kafka/status").asText()).as(health.body()).isEqualTo("UP");
        assertThat(health.json().at("/components/db/status").asText()).as(health.body()).isEqualTo("UP");
    }

    @Test
    @DisplayName("E2E GET /actuator/health/readiness when the service runs should be up without Kafka in it")
    void readiness_whenServiceRuns_shouldBeUpWithoutKafkaInIt() {
        // when
        Response readiness = api.health("/readiness");

        // then
        assertThat(readiness.status()).as(readiness.body()).isEqualTo(200);
        assertThat(readiness.json().at("/status").asText()).as(readiness.body()).isEqualTo("UP");
        assertThat(readiness.json().at("/components/kafka").isMissingNode()).as(readiness.body()).isTrue();
    }
}
