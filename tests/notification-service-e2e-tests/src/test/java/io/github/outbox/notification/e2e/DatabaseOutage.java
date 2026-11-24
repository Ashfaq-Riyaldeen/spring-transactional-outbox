package io.github.outbox.notification.e2e;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Takes the service's database away and gives it back, the way a restart or a failover does: open connections
 * are cut and new ones are refused until {@link #end()}. Works through the {@code postgres} maintenance database,
 * so the switch itself never loses its connection.
 */
final class DatabaseOutage {

    private final JdbcClient admin;
    private final String database;

    DatabaseOutage() {
        String url = NotificationEndToEndContainers.POSTGRES.getJdbcUrl();
        this.database = NotificationEndToEndContainers.POSTGRES.getDatabaseName();
        this.admin = JdbcClient.create(new DriverManagerDataSource(
                url.replace("/" + database, "/postgres"),
                NotificationEndToEndContainers.POSTGRES.getUsername(),
                NotificationEndToEndContainers.POSTGRES.getPassword()
        ));
    }

    void begin() {
        admin.sql("ALTER DATABASE %s ALLOW_CONNECTIONS false".formatted(database)).update();
        admin.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :database")
                .param("database", database)
                .query()
                .listOfRows();
    }

    void end() {
        admin.sql("ALTER DATABASE %s ALLOW_CONNECTIONS true".formatted(database)).update();
    }
}
