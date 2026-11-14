package io.github.outbox.notification.notification;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.api.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    /**
     * Notifies the customer about an order event and stores the notification. Joins the caller's transaction, so
     * the notification is stored together with whatever the caller records about the event.
     *
     * @param eventId id of the event, from the message headers
     * @param event   the event
     * @return the stored notification
     */
    @Transactional
    public Notification notify(UUID eventId, OrderEvent event) {
        NotificationType type = switch (event) {
            case OrderCreatedEvent created -> NotificationType.ORDER_CREATED;
            case OrderCancelledEvent cancelled -> NotificationType.ORDER_CANCELLED;
        };
        Notification notification = repository.save(eventId, event.orderId(), event.customerId(), type,
                NotificationChannel.LOG, clock.instant().truncatedTo(ChronoUnit.MICROS));
        log.info("Notification sent type={} orderId={} customerId={} eventId={} correlationId={}",
                type, event.orderId(), event.customerId(), eventId, event.correlationId());
        return notification;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findByOrderId(long orderId) {
        return repository.findByOrderId(orderId).stream().map(NotificationResponse::from).toList();
    }
}
