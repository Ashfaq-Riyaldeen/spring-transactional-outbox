package io.github.outbox.notification.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEventTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLogsEndToEndTest extends NotificationServiceEndToEndTestBase {

    private final ObjectMapper mapper = new ObjectMapper();

    StructuredLogsEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E logs when an event is consumed should write its line as JSON with the consumer context")
    void logs_whenEventIsConsumed_shouldWriteItsLineAsJsonWithConsumerContext(CapturedOutput output) {
        // given
        long orderId = newOrderId();
        UUID eventId = UUID.randomUUID();

        // when
        orderService.publish(eventId, OrderEventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), "e2e-logs-1", null));

        // then
        JsonNode line = Waits.awaitDelivery()
                .until(() -> logLine(output, "Notification sent", eventId), Optional::isPresent)
                .orElseThrow();
        assertThat(line.get("application").asText()).isEqualTo("notification-service");
        assertThat(line.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(line.get("eventType").asText()).isEqualTo(OrderEventTypes.ORDER_CANCELLED);
        assertThat(line.get("orderId").asText()).isEqualTo(String.valueOf(orderId));
        assertThat(line.get("customerId").asText()).isEqualTo("42");
        assertThat(line.get("correlationId").asText()).isEqualTo("e2e-logs-1");
    }

    @Test
    @DisplayName("E2E logs when an event of a traced request is consumed should log that trace beside the consumer's own")
    void logs_whenEventOfTracedRequestIsConsumed_shouldLogThatTraceBesideTheConsumersOwn(CapturedOutput output) {
        // given
        String originTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        UUID eventId = UUID.randomUUID();

        // when
        orderService.publish(eventId, OrderEventTypes.ORDER_CANCELLED, new OrderCancelledEvent(newOrderId(), 42L,
                Instant.parse("2026-11-14T10:15:30Z"), "e2e-logs-2", "00-" + originTraceId + "-00f067aa0ba902b7-01"));

        // then
        JsonNode line = Waits.awaitDelivery()
                .until(() -> logLine(output, "Notification sent", eventId), Optional::isPresent)
                .orElseThrow();
        assertThat(line.get("originTraceId").asText()).isEqualTo(originTraceId);
        assertThat(line.get("traceId").asText()).as(line.toString()).hasSize(32).isNotEqualTo(originTraceId);
        assertThat(line.get("spanId").asText()).as(line.toString()).hasSize(16);
    }

    private Optional<JsonNode> logLine(CapturedOutput output, String messagePrefix, UUID eventId) {
        return output.getOut().lines()
                .filter(text -> text.startsWith("{") && text.contains(messagePrefix))
                .filter(text -> text.contains(eventId.toString()))
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
