package io.github.outbox.order.domain;

import io.github.dmitriyiliyov.oncebox.aop.OutboxPublish;
import io.github.outbox.contract.OrderEventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Cancels an order and publishes {@link io.github.outbox.contract.OrderCancelledEvent} about it through the outbox.
 * <p>
 * Runs only inside the caller's transaction, so the cancellation and its event are committed together; the event
 * is written by {@link OutboxPublish} from the returned order when the method returns normally.
 */
@Component
@RequiredArgsConstructor
class OrderCancellation {

    private final OrderRepository repository;
    private final Clock clock;

    /**
     * @param id            id of the order
     * @param correlationId correlation id the event is published with
     * @return the order as it is after the cancellation
     * @throws OrderNotFoundException         if there is no such order
     * @throws OrderAlreadyCancelledException if the order is already cancelled
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @OutboxPublish(eventType = OrderEventTypes.ORDER_CANCELLED, payload = "#result.cancelledEvent(#correlationId)")
    public Order cancel(long id, String correlationId) {
        if (!repository.cancel(id, clock.instant().truncatedTo(ChronoUnit.MICROS))) {
            throw repository.exists(id) ? new OrderAlreadyCancelledException(id) : new OrderNotFoundException(id);
        }
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
