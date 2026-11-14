package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderService;
import io.github.outbox.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:15:30Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService service;

    @Test
    @DisplayName("IT create() when the request is valid should answer 201 with the order and its location")
    void create_whenRequestIsValid_shouldAnswer201WithTheOrderAndItsLocation() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class))).thenReturn(createdOrder(7L));

        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items[0].productId").value("sku-1"));
        verify(service).create(new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50"))
        )));
    }

    @Test
    @DisplayName("IT create() when customerId is missing should answer 400 without reaching the service")
    void create_whenCustomerIdIsMissing_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when items are empty should answer 400 without reaching the service")
    void create_whenItemsAreEmpty_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": []}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when an item has zero quantity should answer 400 without reaching the service")
    void create_whenItemHasZeroQuantity_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 0, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when a price has three fraction digits should answer 400 without reaching the service")
    void create_whenPriceHasThreeFractionDigits_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 1, "price": 10.505}]}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private static OrderResponse createdOrder(long id) {
        return new OrderResponse(
                id,
                42L,
                OrderStatus.CREATED,
                new BigDecimal("21.00"),
                CREATED_AT,
                null,
                List.of(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")))
        );
    }
}
