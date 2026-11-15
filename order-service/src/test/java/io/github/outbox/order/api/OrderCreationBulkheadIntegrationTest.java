package io.github.outbox.order.api;

import io.github.outbox.order.bulkhead.OrderCreationBulkhead;
import io.github.outbox.order.config.ClockConfiguration;
import io.github.outbox.order.domain.OrderService;
import io.github.outbox.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A bulkhead of one slot that refuses at once, so a slot taken by the test itself is enough to fill it.
@WebMvcTest(OrderController.class)
@Import(ClockConfiguration.class)
@TestPropertySource(properties = {
        "app.bulkhead.order-creation.max-concurrent=1",
        "app.bulkhead.order-creation.max-wait=0s"
})
class OrderCreationBulkheadIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderCreationBulkhead bulkhead;

    @MockitoBean
    private OrderService service;

    @Test
    @DisplayName("IT create() when every bulkhead slot is taken should answer 503 with Retry-After as a problem")
    void create_whenEveryBulkheadSlotIsTaken_shouldAnswer503WithRetryAfterAsProblem() throws Exception {
        // given
        bulkhead.acquire();

        // when
        ResultActions refused;
        try {
            refused = createOrder();
        } finally {
            bulkhead.release();
        }

        // then
        refused.andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/service-busy"))
                .andExpect(jsonPath("$.title").value("Service busy"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the order is created should give its bulkhead slot back")
    void create_whenOrderIsCreated_shouldGiveItsSlotBack() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(order());

        // when
        createOrder().andExpect(status().isCreated());

        // then
        assertThat(bulkhead.availableSlots()).isEqualTo(1);
    }

    @Test
    @DisplayName("IT create() when the service fails should still give its bulkhead slot back")
    void create_whenServiceFails_shouldStillGiveItsSlotBack() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenThrow(new IllegalStateException("boom"));

        // when
        createOrder().andExpect(status().isInternalServerError());

        // then
        assertThat(bulkhead.availableSlots()).isEqualTo(1);
    }

    private ResultActions createOrder() throws Exception {
        return mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                """));
    }

    private static OrderResponse order() {
        return new OrderResponse(
                7,
                42,
                OrderStatus.CREATED,
                new BigDecimal("21.00"),
                Instant.parse("2026-11-14T10:15:30Z"),
                null,
                List.of(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")))
        );
    }
}
