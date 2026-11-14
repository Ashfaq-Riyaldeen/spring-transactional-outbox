package io.github.outbox.order;

import io.github.outbox.order.support.OrderSchema;
import io.github.outbox.order.support.RequiresDocker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@RequiresDocker
class OrderSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeAll
    static void migrate() throws Exception {
        dataSource = OrderSchema.dataSource(POSTGRES);
        OrderSchema.migrate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("TRUNCATE order_items, orders RESTART IDENTITY").update();
    }

    @Test
    @DisplayName("IT migrate() when applied again should leave the schema and its rows as they were")
    void migrate_whenAppliedAgain_shouldLeaveTheSchemaAndItsRowsAsTheyWere() throws Exception {
        // given
        long orderId = insertOrder("CREATED");

        // when
        OrderSchema.migrate(dataSource);

        // then
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = ?").param(orderId).query(String.class).single())
                .isEqualTo("CREATED");
    }

    @Test
    @DisplayName("IT orders when status is unknown should reject the row")
    void orders_whenStatusIsUnknown_shouldRejectTheRow() {
        // when / then
        assertThatThrownBy(() -> insertOrder("SHIPPED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT order_items when quantity is zero should reject the row")
    void orderItems_whenQuantityIsZero_shouldRejectTheRow() {
        // given
        long orderId = insertOrder("CREATED");

        // when / then
        assertThatThrownBy(() -> insertItem(orderId, 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT order_items when order does not exist should reject the row")
    void orderItems_whenOrderDoesNotExist_shouldRejectTheRow() {
        // when / then
        assertThatThrownBy(() -> insertItem(999L, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT orders when cancelled_at is not set should read it back as null")
    void orders_whenCancelledAtIsNotSet_shouldReadItBackAsNull() {
        // given
        long orderId = insertOrder("CREATED");

        // when
        List<Object> cancelledAt = jdbc.sql("SELECT cancelled_at FROM orders WHERE id = ?")
                .param(orderId)
                .query((rs, rowNum) -> rs.getObject("cancelled_at"))
                .list();

        // then
        assertThat(cancelledAt).containsExactly((Object) null);
    }

    private long insertOrder(String status) {
        return jdbc.sql("""
                        INSERT INTO orders (customer_id, status, total, created_at)
                        VALUES (42, :status, 10.50, now())
                        RETURNING id
                        """)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private void insertItem(long orderId, int quantity) {
        jdbc.sql("""
                        INSERT INTO order_items (order_id, product_id, quantity, price)
                        VALUES (:orderId, 'sku-1', :quantity, 10.50)
                        """)
                .param("orderId", orderId)
                .param("quantity", quantity)
                .update();
    }
}
