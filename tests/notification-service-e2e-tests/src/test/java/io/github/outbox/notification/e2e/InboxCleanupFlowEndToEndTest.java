package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.contract.OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InboxCleanupFlowEndToEndTest extends NotificationServiceEndToEndTestBase {

    private static final Duration EXPIRED = INBOX_TTL.multipliedBy(2);

    private final InboxTable inbox = new InboxTable();

    InboxCleanupFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E cleanup when an inbox id is older than the TTL should remove it and keep the fresh ones")
    void cleanup_whenInboxIdIsOlderThanTheTtl_shouldRemoveItAndKeepTheFreshOnes() {
        // given
        UUID expired = consumed(newOrderId());
        UUID fresh = consumed(newOrderId());

        // when
        inbox.age(expired, EXPIRED);

        // then
        Waits.awaitDelivery().until(() -> !inbox.contains(expired));
        assertThat(inbox.contains(fresh)).as("an id younger than the TTL").isTrue();
    }

    private UUID consumed(long orderId) {
        UUID eventId = UUID.randomUUID();
        orderService.publish(eventId, OrderEventTypes.ORDER_CREATED, new OrderCreatedEvent(orderId, 42L,
                List.of(new OrderLine("sku-1", 1, new BigDecimal("10.50"))), new BigDecimal("10.50"),
                Instant.parse("2026-11-14T10:15:30Z"), "c-1", null));
        Waits.awaitDelivery().until(() -> inbox.contains(eventId));
        return eventId;
    }
}
