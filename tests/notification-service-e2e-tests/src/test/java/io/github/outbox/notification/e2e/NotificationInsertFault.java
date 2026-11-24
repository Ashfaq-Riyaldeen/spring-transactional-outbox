package io.github.outbox.notification.e2e;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Fails the first insert of a notification for one order, from inside the service's own database: a trigger
 * raises on that insert and lets every later one through. The attempts are counted by a sequence, which a rollback
 * does not undo, so the count holds the attempt that failed as well.
 */
final class NotificationInsertFault implements AutoCloseable {

    private final JdbcClient jdbc;
    private final String name;

    private NotificationInsertFault(long orderId) {
        this.jdbc = JdbcClient.create(new DriverManagerDataSource(
                NotificationEndToEndContainers.POSTGRES.getJdbcUrl(),
                NotificationEndToEndContainers.POSTGRES.getUsername(),
                NotificationEndToEndContainers.POSTGRES.getPassword()
        ));
        this.name = "e2e_fault_%d".formatted(orderId);
        jdbc.sql("CREATE SEQUENCE %s".formatted(name)).update();
        jdbc.sql("""
                CREATE FUNCTION %1$s() RETURNS trigger AS $$
                BEGIN
                    IF NEW.order_id = %2$d THEN
                        IF nextval('%1$s') = 1 THEN
                            RAISE EXCEPTION 'injected failure of the first notification for order %2$d';
                        END IF;
                    END IF;
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql
                """.formatted(name, orderId)).update();
        jdbc.sql("CREATE TRIGGER %1$s BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION %1$s()"
                .formatted(name)).update();
    }

    static NotificationInsertFault failFirstInsertFor(long orderId) {
        return new NotificationInsertFault(orderId);
    }

    /**
     * @return how many times a notification for the order was inserted, the failed attempt included
     */
    long attempts() {
        return jdbc.sql("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM %s".formatted(name))
                .query(Long.class)
                .single();
    }

    @Override
    public void close() {
        jdbc.sql("DROP TRIGGER IF EXISTS %1$s ON notifications".formatted(name)).update();
        jdbc.sql("DROP FUNCTION IF EXISTS %1$s()".formatted(name)).update();
        jdbc.sql("DROP SEQUENCE IF EXISTS %1$s".formatted(name)).update();
    }
}
