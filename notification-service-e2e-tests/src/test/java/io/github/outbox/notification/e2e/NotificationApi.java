package io.github.outbox.notification.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * A thin HTTP client over the notification API, answering every status instead of throwing on 4xx and 5xx.
 */
final class NotificationApi {

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    NotificationApi(int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:%d".formatted(port))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    Response findByOrderId(long orderId) {
        ResponseEntity<String> entity = client.get()
                .uri("/api/v1/notifications?orderId={orderId}", orderId)
                .retrieve()
                .toEntity(String.class);
        try {
            return new Response(entity.getStatusCode().value(), mapper.readTree(entity.getBody()), entity.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable response body: " + entity.getBody(), e);
        }
    }

    Response metrics() {
        ResponseEntity<String> entity = client.get().uri("/actuator/prometheus").retrieve().toEntity(String.class);
        return new Response(entity.getStatusCode().value(), null, entity.getBody());
    }

    record Response(int status, JsonNode json, String body) {
    }
}
