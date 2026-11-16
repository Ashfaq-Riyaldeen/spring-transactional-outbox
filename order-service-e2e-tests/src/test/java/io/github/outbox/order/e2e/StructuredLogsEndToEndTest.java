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
    @DisplayName("E2E logs when an order is created should write its log line as one JSON object")
    void logs_whenOrderIsCreated_shouldWriteItsLogLineAsOneJsonObject(CapturedOutput output) {
        // given
        Response created = api.create(orderBody(newCustomerId()), null);
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
