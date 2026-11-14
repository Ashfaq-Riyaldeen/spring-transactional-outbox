package io.github.outbox.notification.notification;

import io.github.outbox.notification.support.NotificationSchema;
import io.github.outbox.notification.support.RequiresDocker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RequiresDocker
class NotificationRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final Instant CREATED_AT = Instant.parse("2026-11-14T10:15:30.123456Z");

    private static DataSource dataSource;

    private JdbcClient jdbc;
    private NotificationRepository tested;

    @BeforeAll
    static void migrate() throws Exception {
        dataSource = NotificationSchema.dataSource(POSTGRES);
        NotificationSchema.migrate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("TRUNCATE notifications RESTART IDENTITY").update();
        tested = new NotificationRepository(jdbc);
    }

    @Test
    @DisplayName("IT save() when a notification is stored should keep every field it was given")
    void save_whenNotificationIsStored_shouldKeepEveryFieldItWasGiven() {
        // when
        Notification saved = tested.save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CANCELLED, NotificationChannel.LOG,
                CREATED_AT);

        // then
        Map<String, Object> row = jdbc.sql("SELECT * FROM notifications WHERE id = ?").param(saved.id()).query().singleRow();
        assertThat(row.get("event_id")).isEqualTo(EVENT_ID);
        assertThat(row.get("order_id")).isEqualTo(7L);
        assertThat(row.get("customer_id")).isEqualTo(42L);
        assertThat(row.get("type")).isEqualTo("ORDER_CANCELLED");
        assertThat(row.get("channel")).isEqualTo("LOG");
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(CREATED_AT);
    }
}
