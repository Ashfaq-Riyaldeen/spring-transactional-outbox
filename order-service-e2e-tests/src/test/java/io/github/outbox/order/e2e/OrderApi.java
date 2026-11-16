package io.github.outbox.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * A thin HTTP client over the order API, answering every status instead of throwing on 4xx and 5xx.
 */
final class OrderApi {

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    OrderApi(int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:%d".formatted(port))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    Response create(String body, String correlationId) {
        return send(client.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> correlationIdHeader(headers, correlationId))
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    Response get(long id) {
        return send(client.get().uri("/api/v1/orders/{id}", id).retrieve().toEntity(String.class));
    }

    Response cancel(long id, String correlationId) {
        return send(client.post()
                .uri("/api/v1/orders/{id}/cancel", id)
                .headers(headers -> correlationIdHeader(headers, correlationId))
                .retrieve()
                .toEntity(String.class));
    }

    Response dlqCount() {
        return send(client.get().uri("/api/outbox-dlq/events/count").retrieve().toEntity(String.class));
    }

    Response health(String path) {
        return send(client.get().uri("/actuator/health" + path).retrieve().toEntity(String.class));
    }

    Response metrics() {
        ResponseEntity<String> entity = client.get().uri("/actuator/prometheus").retrieve().toEntity(String.class);
        return new Response(entity.getStatusCode().value(), entity.getHeaders(), null, entity.getBody());
    }

    private Response send(ResponseEntity<String> entity) {
        try {
            JsonNode body = entity.getBody() == null ? null : mapper.readTree(entity.getBody());
            return new Response(entity.getStatusCode().value(), entity.getHeaders(), body, entity.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable response body: " + entity.getBody(), e);
        }
    }

    private static void correlationIdHeader(HttpHeaders headers, String correlationId) {
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
    }

    record Response(int status, HttpHeaders headers, JsonNode json, String body) {
    }
}
