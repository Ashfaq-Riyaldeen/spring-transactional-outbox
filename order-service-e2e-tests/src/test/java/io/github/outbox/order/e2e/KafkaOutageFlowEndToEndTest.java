package io.github.outbox.order.e2e;

import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.order.e2e.OrderApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOutageFlowEndToEndTest extends OrderServiceEndToEndTestBase {

    KafkaOutageFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E Kafka outage when an order is placed while Kafka is away should accept it and deliver later")
    void outage_whenOrderIsPlacedWhileKafkaIsAway_shouldAcceptItAndDeliverTheEventLater() {
        // given
        long customerId = newCustomerId();
        OrderEndToEndContainers.pauseKafka();
        Response created;
        try {
            // when
            created = api.create(orderBody(customerId), null);

            // then
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            assertThat(api.get(created.json().get("id").asLong()).status()).isEqualTo(200);
        } finally {
            OrderEndToEndContainers.unpauseKafka();
        }
        long orderId = created.json().get("id").asLong();
        Waits.awaitDelivery().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).size() == 1);
    }
}
