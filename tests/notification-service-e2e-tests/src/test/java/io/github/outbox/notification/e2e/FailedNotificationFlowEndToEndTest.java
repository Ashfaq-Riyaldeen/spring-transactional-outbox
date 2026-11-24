package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEventTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FailedNotificationFlowEndToEndTest extends NotificationServiceEndToEndTestBase {

    private final long orderId = newOrderId();
    private final NotificationInsertFault fault = NotificationInsertFault.failFirstInsertFor(orderId);

    FailedNotificationFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @AfterEach
    void removeFault() {
        fault.close();
    }

    @Test
    @DisplayName("E2E failed notification when its insert fails once should roll back the inbox record and notify once")
    void failedNotification_whenItsInsertFailsOnce_shouldRollBackTheInboxRecordAndNotifyOnce() {
        // given
        String correlationId = "failed-notification-" + UUID.randomUUID();

        // when
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), correlationId, null));

        // then - had the inbox record survived the failure, the retry would have skipped the event
        Waits.awaitDelivery().until(() -> api.findByOrderId(orderId).json().size() == 1);
        Waits.awaitQuiet().until(() -> api.findByOrderId(orderId).json().size() == 1);
        assertThat(fault.attempts()).as("inserts of the notification, the failed one included").isEqualTo(2);
    }
}
