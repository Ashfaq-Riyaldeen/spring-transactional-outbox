package io.github.outbox.order.domain;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final Clock clock;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(line -> new OrderItem(line.productId(), line.quantity(), line.price()))
                .toList();
        BigDecimal total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = repository.save(request.customerId(), items, total, now());
        log.info("Order created orderId={} customerId={} total={}", order.id(), order.customerId(), order.total());
        return OrderResponse.from(order);
    }

    // The database keeps microseconds; a finer instant would not read back equal to what was returned.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
