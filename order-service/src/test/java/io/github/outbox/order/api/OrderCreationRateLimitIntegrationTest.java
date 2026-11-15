package io.github.outbox.order.api;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Limits of four orders a minute in total and two a minute per address; every test starts with full limits.
@WebMvcTest(OrderController.class)
@Import(ClockConfiguration.class)
@TestPropertySource(properties = {
        "app.rate-limit.order-creation.global.capacity=4",
        "app.rate-limit.order-creation.global.period=1m",
        "app.rate-limit.order-creation.per-ip.capacity=2",
        "app.rate-limit.order-creation.per-ip.period=1m"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderCreationRateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService service;

    @Test
    @DisplayName("IT create() when the global limit is used up should answer 429 with Retry-After as a problem")
    void create_whenGlobalLimitIsUsedUp_shouldAnswer429WithRetryAfterAsProblem() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(order());
        createOrderFrom("10.0.0.1").andExpect(status().isCreated());
        createOrderFrom("10.0.0.1").andExpect(status().isCreated());
        createOrderFrom("10.0.0.2").andExpect(status().isCreated());
        createOrderFrom("10.0.0.2").andExpect(status().isCreated());

        // when
        ResultActions refused = createOrderFrom("10.0.0.3");

        // then
        refused.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "15"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/rate-limit-exceeded"))
                .andExpect(jsonPath("$.title").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Too many orders are being created, retry later"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verify(service, times(4)).create(any(CreateOrderRequest.class), any());
    }

    @Test
    @DisplayName("IT create() when one address uses up its limit should answer it 429 and still serve another address")
    void create_whenOneAddressUsesUpItsLimit_shouldAnswerIt429AndServeAnotherAddress() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(order());
        createOrderFrom("10.0.0.1").andExpect(status().isCreated());
        createOrderFrom("10.0.0.1").andExpect(status().isCreated());

        // when
        ResultActions refused = createOrderFrom("10.0.0.1");

        // then
        refused.andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.detail").value("Too many orders from this address, retry later"));
        createOrderFrom("10.0.0.2").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("IT get() when orders are read should not take from the order creation limit")
    void get_whenOrdersAreRead_shouldNotTakeFromOrderCreationLimit() throws Exception {
        // given
        when(service.get(anyLong())).thenReturn(order());

        // when / then
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/orders/7")).andExpect(status().isOk());
        }
    }

    private ResultActions createOrderFrom(String clientIp) throws Exception {
        return mvc.perform(post("/api/v1/orders").with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content("""
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
