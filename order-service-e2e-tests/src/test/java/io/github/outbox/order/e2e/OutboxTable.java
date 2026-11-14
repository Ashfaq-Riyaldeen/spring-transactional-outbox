package io.github.outbox.order.e2e;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Optional;

/**
 * Reads the outbox row of an order's event straight from the database - the one place where a failed send that
 * the outbox will retry is visible, since the API shows only the order.
 */
final class OutboxTable {

    private final JdbcClient jdbc;

    OutboxTable() {
        this.jdbc = JdbcClient.create(new DriverManagerDataSource(
                OrderEndToEndContainers.POSTGRES.getJdbcUrl(),
                OrderEndToEndContainers.POSTGRES.getUsername(),
                OrderEndToEndContainers.POSTGRES.getPassword()
        ));
    }

    Optional<Integer> retryCount(String eventType, long orderId) {
        return jdbc.sql("""
                        SELECT retry_count
                        FROM outbox_events
                        WHERE event_type = :eventType AND (payload::jsonb ->> 'orderId')::bigint = :orderId
                        """)
                .param("eventType", eventType)
                .param("orderId", orderId)
                .query(Integer.class)
                .optional();
    }
}
