package io.github.outbox.order.e2e;

import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.order.e2e.OrderApi.Response;
import io.github.outbox.order.e2e.OrderEventsProbe.PublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostOrderEndToEndTest extends OrderServiceEndToEndTestBase {

    PostOrderEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E POST /orders when the order is valid should store it and publish order created once")
    void post_whenOrderIsValid_shouldStoreItAndPublishOrderCreatedOnce() {
        // given
        long customerId = newCustomerId();

        // when
        Response created = api.create(orderBody(customerId), "e2e-post-1");

        // then
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        long orderId = created.json().get("id").asLong();
        Response read = api.get(orderId);
        assertThat(read.status()).as(read.body()).isEqualTo(200);
        assertThat(read.json().get("total").decimalValue()).isEqualByComparingTo("24.00");
        Waits.awaitDelivery().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).size() == 1);
        Waits.awaitQuiet().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).size() == 1);
        PublishedEvent event = events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).getFirst();
        assertThat(UUID.fromString(event.headers().get("oncebox_event_id"))).isNotNull();
        assertThat(event.headers().get("oncebox_event_payload_type")).isEqualTo(OrderCreatedEvent.class.getName());
        assertThat(event.payload().get("customerId").asLong()).isEqualTo(customerId);
        assertThat(event.payload().get("correlationId").asText()).isEqualTo("e2e-post-1");
        assertThat(event.payload().get("lines")).hasSize(2);
    }

    @Test
    @DisplayName("E2E POST /orders when the request is invalid should answer 400 and publish nothing")
    void post_whenRequestIsInvalid_shouldAnswer400AndPublishNothing() {
        // given
        long customerId = newCustomerId();
        String body = """
                {"customerId": %d, "items": [{"productId": "sku-1", "quantity": 0, "price": 10.50}]}
                """.formatted(customerId);

        // when
        Response rejected = api.create(body, null);

        // then
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(400);
        assertThat(rejected.json().get("errors").get(0).get("field").asText()).isEqualTo("items[0].quantity");
        Waits.awaitQuiet().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "customerId", customerId),
                List::isEmpty);
    }
}
