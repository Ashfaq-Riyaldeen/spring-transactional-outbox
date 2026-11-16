package io.github.outbox.order.e2e;

import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.order.e2e.OrderApi.Response;
import io.github.outbox.order.e2e.OrderEventsProbe.PublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

class CancelOrderFlowEndToEndTest extends OrderServiceEndToEndTestBase {

    CancelOrderFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E cancel flow when a created order is cancelled should publish order cancelled with its correlation id")
    void cancel_whenCreatedOrderIsCancelled_shouldPublishOrderCancelledWithItsCorrelationId() {
        // given
        long orderId = createOrder();

        // when
        Response cancelled = api.cancel(orderId, "e2e-cancel-1");

        // then
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.json().get("status").asText()).isEqualTo("CANCELLED");
        Waits.awaitDelivery()
                .until(() -> events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).size() == 1);
        PublishedEvent event = events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).getFirst();
        assertThat(event.payload().get("correlationId").asText()).isEqualTo("e2e-cancel-1");
        assertThat(event.payload().get("occurredAt").asText()).isEqualTo(cancelled.json().get("cancelledAt").asText());
    }

    @Test
    @DisplayName("E2E cancel flow when the cancelling request is traced should publish order cancelled in its trace")
    void cancel_whenCancellingRequestIsTraced_shouldPublishOrderCancelledInItsTrace() {
        // given
        long orderId = createOrder();
        String traceId = "0af7651916cd43dd8448eb211c80319c";

        // when
        Response cancelled = api.cancelTraced(orderId, "00-" + traceId + "-b7ad6b7169203331-01");

        // then
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        Waits.awaitDelivery()
                .until(() -> events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).size() == 1);
        PublishedEvent event = events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).getFirst();
        assertThat(event.payload().get("traceParent").asText()).matches("00-" + traceId + "-[0-9a-f]{16}-01");
    }

    @Test
    @DisplayName("E2E cancel flow when an order is cancelled twice should answer 409 and publish one cancellation")
    void cancel_whenOrderIsCancelledTwice_shouldAnswer409AndPublishOneCancellation() {
        // given
        long orderId = createOrder();
        Response first = api.cancel(orderId, null);
        assertThat(first.status()).as(first.body()).isEqualTo(200);

        // when
        Response second = api.cancel(orderId, null);

        // then
        assertThat(second.status()).as(second.body()).isEqualTo(409);
        assertThat(second.json().get("type").asText())
                .isEqualTo("https://spring-transactional-outbox/errors/order-already-cancelled");
        Waits.awaitDelivery()
                .until(() -> events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).size() == 1);
        Waits.awaitQuiet()
                .until(() -> events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", orderId).size() == 1);
    }

    @Test
    @DisplayName("E2E cancel flow when the order does not exist should answer 404 and publish nothing")
    void cancel_whenOrderDoesNotExist_shouldAnswer404AndPublishNothing() {
        // when
        Response response = api.cancel(Long.MAX_VALUE, null);

        // then
        assertThat(response.status()).as(response.body()).isEqualTo(404);
        Waits.awaitQuiet()
                .until(() -> events.events(OrderEventTypes.ORDER_CANCELLED, "orderId", Long.MAX_VALUE).isEmpty());
    }

    private long createOrder() {
        Response created = api.create(orderBody(newCustomerId()), null);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return created.json().get("id").asLong();
    }
}
