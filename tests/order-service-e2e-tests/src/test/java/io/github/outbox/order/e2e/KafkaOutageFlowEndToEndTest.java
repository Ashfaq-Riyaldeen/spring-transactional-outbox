package io.github.outbox.order.e2e;

import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.order.e2e.OrderApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOutageFlowEndToEndTest extends OrderServiceEndToEndTestBase {

    private final OutboxTable outbox = new OutboxTable();

    KafkaOutageFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E Kafka outage when a send fails while Kafka is away should retry it and deliver it once")
    void outage_whenSendFailsWhileKafkaIsAway_shouldRetryItAndDeliverItOnce() {
        // given
        long customerId = newCustomerId();
        OrderEndToEndContainers.pauseKafka();
        long orderId;
        try {
            // when
            Response created = api.create(orderBody(customerId), null);
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            orderId = created.json().get("id").asLong();
            int initialRetryCount = outbox.retryCount(OrderEventTypes.ORDER_CREATED, orderId).orElseThrow();

            // then - the send has failed and the event is scheduled for a retry, not lost
            Waits.awaitDelivery().until(() -> outbox.retryCount(OrderEventTypes.ORDER_CREATED, orderId)
                    .orElseThrow() > initialRetryCount);
            assertThat(events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId)).isEmpty();
        } finally {
            OrderEndToEndContainers.unpauseKafka();
        }
        Waits.awaitDelivery().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).size() == 1);
        Waits.awaitQuiet().until(() -> events.events(OrderEventTypes.ORDER_CREATED, "orderId", orderId).size() == 1);
    }
}
