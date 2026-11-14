package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.inbox.EventIdExtractor;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventsListenerUnitTest {

    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final OrderEvent EVENT = new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"),
            "c-1", null);

    @Mock
    private NotificationService notificationService;

    private RecordingIdempotentConsumer idempotentConsumer;
    private OrderEventsListener tested;

    @BeforeEach
    void setUp() {
        idempotentConsumer = new RecordingIdempotentConsumer();
        tested = new OrderEventsListener(idempotentConsumer, notificationService);
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event is new should notify about it under the id from its header")
    void onOrderEvent_whenEventIsNew_shouldNotifyAboutItUnderTheIdFromItsHeader() {
        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        assertThat(idempotentConsumer.seen).containsExactly(EVENT_ID);
        verify(notificationService).notify(EVENT_ID, EVENT);
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event was consumed before should notify nobody")
    void onOrderEvent_whenEventWasConsumedBefore_shouldNotifyNobody() {
        // given
        idempotentConsumer.seen.add(EVENT_ID);

        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        verifyNoInteractions(notificationService);
    }

    private static Message<OrderEvent> message(UUID eventId) {
        return MessageBuilder.withPayload(EVENT).setHeader("oncebox_event_id", eventId.toString()).build();
    }

    /**
     * Runs the operation for an id it has not seen and skips it for one it has, as the inbox does.
     */
    private static final class RecordingIdempotentConsumer implements IdempotentConsumer {

        private final Set<UUID> seen = new HashSet<>();

        @Override
        public void consume(UUID eventId, Runnable operation) {
            if (seen.add(eventId)) {
                operation.run();
            }
        }

        @Override
        public <T> void consume(T message, EventIdExtractor<T> idExtractor, Consumer<T> operation) {
            consume(idExtractor.extract(message), () -> operation.accept(message));
        }

        @Override
        public void consume(Set<UUID> ids, Consumer<Set<UUID>> operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void consume(List<T> messages, EventIdExtractor<T> idExtractor, Consumer<List<T>> operation) {
            throw new UnsupportedOperationException();
        }
    }
}
