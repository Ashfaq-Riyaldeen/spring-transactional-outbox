package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderService;
import io.github.outbox.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(OrderControllerIntegrationTest.FixedClockConfiguration.class)
class OrderControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30Z");

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
    @DisplayName("IT create() when customerId is missing should answer a validation problem naming the field")
    void create_whenCustomerIdIsMissing_shouldAnswerValidationProblemNamingTheField() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/orders"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("customerId"))
                .andExpect(jsonPath("$.errors[0].message").value("customerId must not be null"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when items are empty should answer a validation problem naming items")
    void create_whenItemsAreEmpty_shouldAnswerValidationProblemNamingItems() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": []}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"))
                .andExpect(jsonPath("$.errors[0].message").value("items must not be empty"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when an item has zero quantity should name the field by its path in the body")
    void create_whenItemHasZeroQuantity_shouldNameTheFieldByItsPathInTheBody() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 0, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("quantity must be positive"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when a price has three fraction digits should answer 400 without reaching the service")
    void create_whenPriceHasThreeFractionDigits_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 1, "price": 10.505}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].price"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the body is not JSON should answer Spring's 400 problem stamped with path")
    void create_whenBodyIsNotJson_shouldAnswerSpringsProblemStampedWithPath() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the body is not JSON media type should answer 415")
    void create_whenBodyIsNotJsonMediaType_shouldAnswer415() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.TEXT_PLAIN).content("order"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the service fails unexpectedly should answer 500 without the cause's message")
    void create_whenServiceFailsUnexpectedly_shouldAnswer500WithoutTheCausesMessage() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class)))
                .thenThrow(new IllegalStateException("relation \"orders\" does not exist"));

        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/internal-error"))
                .andExpect(jsonPath("$.detail").value("The operation could not be completed"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    private static OrderResponse createdOrder(long id) {
        return new OrderResponse(
                id,
                42L,
                OrderStatus.CREATED,
                new BigDecimal("21.00"),
                NOW,
                null,
                List.of(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")))
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
