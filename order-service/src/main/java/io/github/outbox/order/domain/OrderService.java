package io.github.outbox.order.domain;

import io.github.dmitriyiliyov.oncebox.core.outbox.OutboxPublisher;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.contract.OrderLine;
import io.github.outbox.order.api.CreateOrderRequest;
import io.github.outbox.order.api.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    /**
     * Stores a new order and, in the same transaction, the {@link OrderCreatedEvent} about it: either both are
     * committed or neither is.
     *
     * @param request       the order as the client sent it
     * @param correlationId correlation id of the request; a new one is generated when it is {@code null} or blank
     * @return the stored order
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String correlationId) {
        List<OrderItem> items = request.items().stream()
                .map(line -> new OrderItem(line.productId(), line.quantity(), line.price()))
                .toList();
        BigDecimal total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = repository.save(request.customerId(), items, total, now());
        outboxPublisher.publish(OrderEventTypes.ORDER_CREATED, createdEvent(order, correlationId));
        log.info("Order created orderId={} customerId={} total={}", order.id(), order.customerId(), order.total());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long id) {
        return repository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static OrderCreatedEvent createdEvent(Order order, String correlationId) {
        List<OrderLine> lines = order.items().stream()
                .map(item -> new OrderLine(item.productId(), item.quantity(), item.price()))
                .toList();
        return new OrderCreatedEvent(
                order.id(),
                order.customerId(),
                lines,
                order.total(),
                order.createdAt(),
                correlationIdOrNew(correlationId),
                null
        );
    }

    private static String correlationIdOrNew(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    // The database keeps microseconds; a finer instant would not read back equal to what was returned.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
