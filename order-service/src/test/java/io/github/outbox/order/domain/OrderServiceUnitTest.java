package io.github.outbox.order.domain;

import io.github.dmitriyiliyov.oncebox.core.outbox.OutboxPublisher;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.contract.OrderLine;
import io.github.outbox.order.api.CreateOrderRequest;
import io.github.outbox.order.api.OrderItemRequest;
import io.github.outbox.order.api.OrderItemResponse;
import io.github.outbox.order.api.OrderResponse;
import io.github.outbox.order.ratelimit.OrderCreationRateLimiter;
import io.github.outbox.order.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    // Nanoseconds on purpose: the stored time is cut to what the database keeps.
    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-14T10:15:30.123456Z");

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderCancellation cancellation;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private OrderCreationRateLimiter rateLimiter;

    private OrderService tested;

    @BeforeEach
    void setUp() {
        tested = new OrderService(repository, cancellation, outboxPublisher, rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("UT create() when the customer's rate limit is used up should refuse without storing or publishing")
    void create_whenCustomerRateLimitIsUsedUp_shouldRefuseWithoutStoringOrPublishing() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 1, new BigDecimal("10.50"))
        ));
        RateLimitExceededException refusal =
                new RateLimitExceededException("Too many orders for customer 42, retry later", Duration.ofSeconds(6));
        doThrow(refusal).when(rateLimiter).acquireForCustomer(42L);

        // when / then
        assertThatThrownBy(() -> tested.create(request, "c-1")).isSameAs(refusal);
        verifyNoInteractions(repository, outboxPublisher);
    }

    @Test
    @DisplayName("UT create() when several items are ordered should store the sum of their subtotals as total")
    void create_whenSeveralItemsAreOrdered_shouldStoreTheSumOfTheirSubtotalsAsTotal() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50")),
                new OrderItemRequest("sku-2", 3, new BigDecimal("1.25"))
        ));
        givenRepositoryStoresUnderId(7L);

        // when
        OrderResponse response = tested.create(request, "c-1");

        // then
        verify(repository).save(
                eq(42L),
                eq(List.of(
                        new OrderItem("sku-1", 2, new BigDecimal("10.50")),
                        new OrderItem("sku-2", 3, new BigDecimal("1.25"))
                )),
                eq(new BigDecimal("24.75")),
                eq(NOW_IN_MICROS)
        );
        assertThat(response.total()).isEqualByComparingTo("24.75");
    }

    @Test
    @DisplayName("UT create() when prices come with any scale should store them and the total with two decimals")
    void create_whenPricesComeWithAnyScale_shouldStoreThemAndTheTotalWithTwoDecimals() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.5")),
                new OrderItemRequest("sku-2", 1, new BigDecimal("1E+2"))
        ));
        givenRepositoryStoresUnderId(7L);

        // when
        OrderResponse response = tested.create(request, "c-1");

        // then
        assertThat(response.total().toPlainString()).isEqualTo("121.00");
        assertThat(response.items()).extracting(item -> item.price().toPlainString())
                .containsExactly("10.50", "100.00");
    }

    @Test
    @DisplayName("UT create() when the order is stored should return it as created with its items")
    void create_whenOrderIsStored_shouldReturnItAsCreatedWithItsItems() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50"))
        ));
        givenRepositoryStoresUnderId(7L);

        // when
        OrderResponse response = tested.create(request, "c-1");

        // then
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.customerId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.createdAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(response.cancelledAt()).isNull();
        assertThat(response.items()).containsExactly(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")));
    }

    @Test
    @DisplayName("UT create() when the order is stored should publish order created with its lines and correlation id")
    void create_whenOrderIsStored_shouldPublishOrderCreatedWithItsLinesAndCorrelationId() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50"))
        ));
        givenRepositoryStoresUnderId(7L);

        // when
        tested.create(request, "c-1");

        // then
        InOrder inOrder = inOrder(repository, outboxPublisher);
        inOrder.verify(repository).save(anyLong(), anyList(), any(BigDecimal.class), any(Instant.class));
        inOrder.verify(outboxPublisher).publish(OrderEventTypes.ORDER_CREATED, new OrderCreatedEvent(
                7L,
                42L,
                List.of(new OrderLine("sku-1", 2, new BigDecimal("10.50"))),
                new BigDecimal("21.00"),
                NOW_IN_MICROS,
                "c-1",
                null
        ));
    }

    @Test
    @DisplayName("UT create() when no correlation id is given should publish the event under a generated one")
    void create_whenNoCorrelationIdIsGiven_shouldPublishTheEventUnderGeneratedOne() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50"))
        ));
        givenRepositoryStoresUnderId(7L);
        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);

        // when
        tested.create(request, " ");

        // then
        verify(outboxPublisher).publish(eq(OrderEventTypes.ORDER_CREATED), event.capture());
        assertThat(event.getValue().correlationId()).isNotBlank();
        assertThat(UUID.fromString(event.getValue().correlationId())).isNotNull();
    }

    @Test
    @DisplayName("UT create() when the order is stored should name its customer and id in the MDC")
    void create_whenOrderIsStored_shouldNameItsCustomerAndIdInMdc() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 1, new BigDecimal("10.50"))
        ));
        givenRepositoryStoresUnderId(7L);

        // when
        tested.create(request, "c-1");

        // then
        assertThat(MDC.get(OrderService.MDC_CUSTOMER_ID)).isEqualTo("42");
        assertThat(MDC.get(OrderService.MDC_ORDER_ID)).isEqualTo("7");
    }

    @Test
    @DisplayName("UT cancel() when the order is cancelled should name it and its customer in the MDC")
    void cancel_whenOrderIsCancelled_shouldNameItAndItsCustomerInMdc() {
        // given
        Order cancelled = new Order(7L, 42L, OrderStatus.CANCELLED, BigDecimal.ONE, NOW_IN_MICROS, NOW_IN_MICROS,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
        when(cancellation.cancel(7L, "c-1")).thenReturn(cancelled);

        // when
        tested.cancel(7L, "c-1");

        // then
        assertThat(MDC.get(OrderService.MDC_ORDER_ID)).isEqualTo("7");
        assertThat(MDC.get(OrderService.MDC_CUSTOMER_ID)).isEqualTo("42");
    }

    @Test
    @DisplayName("UT cancel() when the cancellation succeeds should return the cancelled order")
    void cancel_whenCancellationSucceeds_shouldReturnTheCancelledOrder() {
        // given
        Order cancelled = new Order(7L, 42L, OrderStatus.CANCELLED, BigDecimal.ONE, NOW_IN_MICROS, NOW_IN_MICROS,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
        when(cancellation.cancel(7L, "c-1")).thenReturn(cancelled);

        // when
        OrderResponse response = tested.cancel(7L, "c-1");

        // then
        assertThat(response).isEqualTo(OrderResponse.from(cancelled));
    }

    @Test
    @DisplayName("UT cancel() when no correlation id is given should cancel under a generated one")
    void cancel_whenNoCorrelationIdIsGiven_shouldCancelUnderGeneratedOne() {
        // given
        Order cancelled = new Order(7L, 42L, OrderStatus.CANCELLED, BigDecimal.ONE, NOW_IN_MICROS, NOW_IN_MICROS,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        when(cancellation.cancel(eq(7L), correlationId.capture())).thenReturn(cancelled);

        // when
        tested.cancel(7L, null);

        // then
        assertThat(UUID.fromString(correlationId.getValue())).isNotNull();
    }

    @Test
    @DisplayName("UT get() when the order exists should return it")
    void get_whenOrderExists_shouldReturnIt() {
        // given
        Order order = new Order(7L, 42L, OrderStatus.CREATED, BigDecimal.ONE, NOW_IN_MICROS, null,
                List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)));
        when(repository.findById(7L)).thenReturn(Optional.of(order));

        // when
        OrderResponse response = tested.get(7L);

        // then
        assertThat(response).isEqualTo(OrderResponse.from(order));
    }

    @Test
    @DisplayName("UT get() when the order does not exist should throw OrderNotFoundException")
    void get_whenOrderDoesNotExist_shouldThrowOrderNotFoundException() {
        // given
        when(repository.findById(7L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.get(7L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order 7 not found");
    }

    private void givenRepositoryStoresUnderId(long id) {
        when(repository.save(anyLong(), anyList(), any(BigDecimal.class), any(Instant.class)))
                .thenAnswer(invocation -> new Order(
                        id,
                        invocation.getArgument(0),
                        OrderStatus.CREATED,
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        null,
                        invocation.getArgument(1)
                ));
    }
}
