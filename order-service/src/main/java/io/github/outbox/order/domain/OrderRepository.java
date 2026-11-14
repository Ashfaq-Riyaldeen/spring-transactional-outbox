package io.github.outbox.order.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final JdbcClient jdbc;

    /**
     * Stores a new order in status {@link OrderStatus#CREATED} together with its items.
     * Expected to run inside the caller's transaction, so the order and its items are stored together or not at all.
     *
     * @param customerId customer who placed the order
     * @param items      the ordered items
     * @param total      sum of the subtotals of the items
     * @param createdAt  the moment to record as the creation time
     * @return the stored order with its generated id
     */
    public Order save(long customerId, List<OrderItem> items, BigDecimal total, Instant createdAt) {
        long id = jdbc.sql("""
                        INSERT INTO orders (customer_id, status, total, created_at)
                        VALUES (:customerId, :status, :total, :createdAt)
                        RETURNING id
                        """)
                .param("customerId", customerId)
                .param("status", OrderStatus.CREATED.name())
                .param("total", total)
                .param("createdAt", Timestamp.from(createdAt))
                .query(Long.class)
                .single();
        for (OrderItem item : items) {
            jdbc.sql("""
                            INSERT INTO order_items (order_id, product_id, quantity, price)
                            VALUES (:orderId, :productId, :quantity, :price)
                            """)
                    .param("orderId", id)
                    .param("productId", item.productId())
                    .param("quantity", item.quantity())
                    .param("price", item.price())
                    .update();
        }
        return new Order(id, customerId, OrderStatus.CREATED, total, createdAt, null, items);
    }

    public Optional<Order> findById(long id) {
        List<OrderItem> items = jdbc.sql("""
                        SELECT product_id, quantity, price
                        FROM order_items
                        WHERE order_id = :orderId
                        ORDER BY id
                        """)
                .param("orderId", id)
                .query((rs, rowNum) -> new OrderItem(
                        rs.getString("product_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price")
                ))
                .list();
        return jdbc.sql("""
                        SELECT id, customer_id, status, total, created_at, cancelled_at
                        FROM orders
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new Order(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("total"),
                        rs.getTimestamp("created_at").toInstant(),
                        toInstant(rs.getTimestamp("cancelled_at")),
                        items
                ))
                .optional();
    }

    /**
     * Moves an order from {@link OrderStatus#CREATED} to {@link OrderStatus#CANCELLED}. The check and the change are
     * one statement, so of two concurrent cancellations exactly one succeeds.
     *
     * @param id          id of the order
     * @param cancelledAt the moment to record as the cancellation time
     * @return {@code true} if the order was cancelled by this call; {@code false} if it does not exist or was not in
     *         status {@link OrderStatus#CREATED}
     */
    public boolean cancel(long id, Instant cancelledAt) {
        return jdbc.sql("""
                        UPDATE orders
                        SET status = :cancelled, cancelled_at = :cancelledAt
                        WHERE id = :id AND status = :created
                        """)
                .param("cancelled", OrderStatus.CANCELLED.name())
                .param("cancelledAt", Timestamp.from(cancelledAt))
                .param("id", id)
                .param("created", OrderStatus.CREATED.name())
                .update() == 1;
    }

    public boolean exists(long id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM orders WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
