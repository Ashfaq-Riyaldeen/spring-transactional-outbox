package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.OnceboxHeadersUtils;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.dmitriyiliyov.oncebox.starter.inbox.TransactionalConsumer;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Turns each order event into exactly one notification, however many times the event is delivered.
 * <p>
 * The inbox records the event id in the same transaction as the notification: a redelivered event finds its id
 * and is skipped, and a notification that fails rolls the id back with it, so the redelivery runs it again. The
 * payload class is resolved from the event type header by the inbox container factory.
 */
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    @TransactionalConsumer
    private final IdempotentConsumer idempotentConsumer;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.order-events-topic}",
            containerFactory = "inboxKafkaListenerContainerFactory"
    )
    public void onOrderEvent(Message<OrderEvent> message) {
        idempotentConsumer.consume(
                message,
                OnceboxHeadersUtils::extractId,
                consumed -> notificationService.notify(OnceboxHeadersUtils.extractId(consumed), consumed.getPayload())
        );
    }
}
