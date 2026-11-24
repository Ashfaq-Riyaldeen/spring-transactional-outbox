package io.github.outbox.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.outbox.order.e2e.OrderApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLogsEndToEndTest extends OrderServiceEndToEndTestBase {

    private final ObjectMapper mapper = new ObjectMapper();

    StructuredLogsEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E logs when an order is created should write its line as JSON with the request context")
    void logs_whenOrderIsCreated_shouldWriteItsLineAsJsonWithRequestContext(CapturedOutput output) {
        // given
        long customerId = newCustomerId();
        Response created = api.create(orderBody(customerId), "e2e-logs-1");
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String expected = "Order created orderId=" + created.json().get("id").asLong() + " ";

        // when
        JsonNode line = Waits.awaitDelivery()
                .until(() -> logLine(output, expected), Optional::isPresent)
                .orElseThrow();

        // then
        assertThat(line.get("level").asText()).isEqualTo("INFO");
        assertThat(line.get("application").asText()).isEqualTo("order-service");
        assertThat(line.get("logger_name").asText()).isEqualTo("io.github.outbox.order.domain.OrderService");
        assertThat(line.hasNonNull("@timestamp")).as(line.toString()).isTrue();
        assertThat(line.hasNonNull("thread_name")).as(line.toString()).isTrue();
        assertThat(line.get("correlationId").asText()).isEqualTo("e2e-logs-1");
        assertThat(line.get("customerId").asText()).isEqualTo(String.valueOf(customerId));
        assertThat(line.get("orderId").asText()).isEqualTo(created.json().get("id").asText());
    }

    @Test
    @DisplayName("E2E logs when a traced request creates an order should log it under the caller's trace id")
    void logs_whenTracedRequestCreatesOrder_shouldLogItUnderTheCallersTraceId(CapturedOutput output) {
        // given
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        // when
        Response created = api.createTraced(orderBody(newCustomerId()), "00-" + traceId + "-00f067aa0ba902b7-01");

        // then
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String expected = "Order created orderId=" + created.json().get("id").asLong() + " ";
        JsonNode line = Waits.awaitDelivery()
                .until(() -> logLine(output, expected), Optional::isPresent)
                .orElseThrow();
        assertThat(line.get("traceId").asText()).isEqualTo(traceId);
        assertThat(line.get("spanId").asText()).hasSize(16).isNotEqualTo("00f067aa0ba902b7");
    }

    private Optional<JsonNode> logLine(CapturedOutput output, String messagePrefix) {
        return output.getOut().lines()
                .filter(text -> text.startsWith("{") && text.contains(messagePrefix))
                .findFirst()
                .map(this::parse);
    }

    private JsonNode parse(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Not a JSON log line: " + text, e);
        }
    }
}
