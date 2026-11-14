package io.github.outbox.order.domain;

import io.github.outbox.order.support.OrderSchema;
import io.github.outbox.order.support.RequiresDocker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RequiresDocker
class OrderRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:15:30.123456Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-11-14T11:00:00.654321Z");

    private static DataSource dataSource;

    private JdbcClient jdbc;
    private OrderRepository tested;

    @BeforeAll
    static void migrate() throws Exception {
        dataSource = OrderSchema.dataSource(POSTGRES);
        OrderSchema.migrate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("TRUNCATE order_items, orders RESTART IDENTITY").update();
        tested = new OrderRepository(jdbc);
    }

    @Test
    @DisplayName("IT save() when an order has items should store the order as created and every item under it")
    void save_whenOrderHasItems_shouldStoreTheOrderAsCreatedAndEveryItemUnderIt() {
        // given
        List<OrderItem> items = List.of(
                new OrderItem("sku-1", 2, new BigDecimal("10.50")),
                new OrderItem("sku-2", 1, new BigDecimal("3.00"))
        );

        // when
        Order saved = tested.save(42L, items, new BigDecimal("24.00"), CREATED_AT);

        // then
        Map<String, Object> order = jdbc.sql("SELECT * FROM orders WHERE id = ?").param(saved.id()).query().singleRow();
        assertThat(order.get("customer_id")).isEqualTo(42L);
        assertThat(order.get("status")).isEqualTo("CREATED");
        assertThat((BigDecimal) order.get("total")).isEqualByComparingTo("24.00");
        assertThat(((java.sql.Timestamp) order.get("created_at")).toInstant()).isEqualTo(CREATED_AT);
        assertThat(order.get("cancelled_at")).isNull();
        assertThat(storedItems(saved.id())).containsExactlyInAnyOrderElementsOf(items);
    }

    @Test
    @DisplayName("IT save() when called twice should give each order its own id")
    void save_whenCalledTwice_shouldGiveEachOrderItsOwnId() {
        // given
        List<OrderItem> items = List.of(new OrderItem("sku-1", 1, BigDecimal.ONE));

        // when
        Order first = tested.save(42L, items, BigDecimal.ONE, CREATED_AT);
        Order second = tested.save(42L, items, BigDecimal.ONE, CREATED_AT);

        // then
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(storedItems(first.id())).hasSize(1);
        assertThat(storedItems(second.id())).hasSize(1);
    }

    @Test
    @DisplayName("IT findById() when the order exists should read it back with its items and no cancellation time")
    void findById_whenOrderExists_shouldReadItBackWithItsItemsAndNoCancellationTime() {
        // given
        List<OrderItem> items = List.of(
                new OrderItem("sku-1", 2, new BigDecimal("10.50")),
                new OrderItem("sku-2", 1, new BigDecimal("3.00"))
        );
        Order saved = tested.save(42L, items, new BigDecimal("24.00"), CREATED_AT);

        // when
        Optional<Order> found = tested.findById(saved.id());

        // then
        assertThat(found).contains(saved);
    }

    @Test
    @DisplayName("IT findById() when the order does not exist should return empty")
    void findById_whenOrderDoesNotExist_shouldReturnEmpty() {
        // when
        Optional<Order> found = tested.findById(999L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("IT cancel() when the order is created should cancel it and record the time")
    void cancel_whenOrderIsCreated_shouldCancelItAndRecordTheTime() {
        // given
        Order saved = tested.save(42L, List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)), BigDecimal.ONE, CREATED_AT);

        // when
        boolean cancelled = tested.cancel(saved.id(), CANCELLED_AT);

        // then
        assertThat(cancelled).isTrue();
        Order found = tested.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(found.cancelledAt()).isEqualTo(CANCELLED_AT);
    }

    @Test
    @DisplayName("IT cancel() when the order is already cancelled should return false and keep the first time")
    void cancel_whenOrderIsAlreadyCancelled_shouldReturnFalseAndKeepTheFirstTime() {
        // given
        Order saved = tested.save(42L, List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)), BigDecimal.ONE, CREATED_AT);
        tested.cancel(saved.id(), CANCELLED_AT);

        // when
        boolean cancelled = tested.cancel(saved.id(), CANCELLED_AT.plusSeconds(60));

        // then
        assertThat(cancelled).isFalse();
        assertThat(tested.findById(saved.id()).orElseThrow().cancelledAt()).isEqualTo(CANCELLED_AT);
    }

    @Test
    @DisplayName("IT cancel() when the order does not exist should return false")
    void cancel_whenOrderDoesNotExist_shouldReturnFalse() {
        // when
        boolean cancelled = tested.cancel(999L, CANCELLED_AT);

        // then
        assertThat(cancelled).isFalse();
    }

    @Test
    @DisplayName("IT exists() when the order is stored should return true and false for any other id")
    void exists_whenOrderIsStored_shouldReturnTrueAndFalseForAnyOtherId() {
        // given
        Order saved = tested.save(42L, List.of(new OrderItem("sku-1", 1, BigDecimal.ONE)), BigDecimal.ONE, CREATED_AT);

        // when / then
        assertThat(tested.exists(saved.id())).isTrue();
        assertThat(tested.exists(saved.id() + 1)).isFalse();
    }

    private List<OrderItem> storedItems(long orderId) {
        return jdbc.sql("SELECT product_id, quantity, price FROM order_items WHERE order_id = ?")
                .param(orderId)
                .query((rs, rowNum) -> new OrderItem(
                        rs.getString("product_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price")
                ))
                .list();
    }
}
