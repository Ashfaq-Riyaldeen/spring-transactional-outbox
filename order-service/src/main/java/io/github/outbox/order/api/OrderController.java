package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final OrderService service;

    @PostMapping
    @Override
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestBody CreateOrderRequest request
    ) {
        OrderResponse order = service.create(request, correlationId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @PostMapping("/{id}/cancel")
    @Override
    public OrderResponse cancel(
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId,
            @PathVariable("id") Long id
    ) {
        return service.cancel(id, correlationId);
    }

    @GetMapping("/{id}")
    @Override
    public OrderResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }
}
