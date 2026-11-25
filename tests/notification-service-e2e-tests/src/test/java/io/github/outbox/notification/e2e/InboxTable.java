package io.github.outbox.notification.e2e;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inbox table of the service, read and aged from outside it: no API tells which event ids the inbox holds, and
 * an id becomes old enough for the cleanup only by waiting out its TTL.
 */
final class InboxTable {

    private final JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(
            NotificationEndToEndContainers.POSTGRES.getJdbcUrl(),
            NotificationEndToEndContainers.POSTGRES.getUsername(),
            NotificationEndToEndContainers.POSTGRES.getPassword()
    ));

    boolean contains(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM inbox_events WHERE id = :id")
                .param("id", eventId)
                .query(Long.class)
                .single() == 1L;
    }

    void age(UUID eventId, Duration age) {
        int updated = jdbc.sql("""
                        UPDATE inbox_events SET consumed_at = now() - make_interval(secs => :seconds)
                        WHERE id = :id
                        """)
                .param("seconds", age.toSeconds())
                .param("id", eventId)
                .update();
        assertThat(updated).as("inbox ids aged for event %s", eventId).isEqualTo(1);
    }
}
