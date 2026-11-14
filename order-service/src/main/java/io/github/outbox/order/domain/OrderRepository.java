package io.github.outbox.order.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
}
