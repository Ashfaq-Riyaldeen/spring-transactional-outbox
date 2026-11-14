package io.github.outbox.notification.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification as it is stored.
 *
 * @param id         id of the notification
 * @param eventId    id of the order event it was sent for
 * @param orderId    order it is about
 * @param customerId customer it was sent to
 * @param type       what happened to the order
 * @param channel    where it was delivered
 * @param createdAt  the moment it was stored
 */
public record Notification(long id,
                           UUID eventId,
                           long orderId,
                           long customerId,
                           NotificationType type,
                           NotificationChannel channel,
                           Instant createdAt) {
}
