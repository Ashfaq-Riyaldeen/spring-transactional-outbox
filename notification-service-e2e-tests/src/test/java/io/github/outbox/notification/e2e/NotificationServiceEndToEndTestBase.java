package io.github.outbox.notification.e2e;

import io.github.outbox.notification.NotificationServiceApplication;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The notification service started as in production - its own application class and configuration - over the
 * shared Postgres and Kafka. The order service is played by {@link OrderEventsPublisher}.
 */
@SpringBootTest(classes = NotificationServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@RequiresDocker
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class NotificationServiceEndToEndTestBase {

    static final String TOPIC = "order-events";
    static final String DEAD_LETTER_TOPIC = "order-events-dlt";

    protected final NotificationApi api;
    protected final OrderEventsPublisher orderService;

    protected NotificationServiceEndToEndTestBase(int port) {
        this.api = new NotificationApi(port);
        this.orderService = new OrderEventsPublisher(NotificationEndToEndContainers.KAFKA.getBootstrapServers(), TOPIC);
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", NotificationEndToEndContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", NotificationEndToEndContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", NotificationEndToEndContainers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", NotificationEndToEndContainers.KAFKA::getBootstrapServers);
        registry.add("app.kafka.order-events-topic", () -> TOPIC);
        registry.add("app.kafka.retry-interval", () -> "200ms");
        registry.add("app.kafka.transient-retry.initial-interval", () -> "200ms");
        registry.add("app.kafka.transient-retry.max-interval", () -> "1s");
    }

    @AfterEach
    void closePublisher() {
        orderService.close();
    }

    // E2E state is never cleaned, so every case works with orders of its own.
    static long newOrderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }
}
