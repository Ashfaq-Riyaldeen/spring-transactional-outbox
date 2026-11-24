package io.github.outbox.order.e2e;

import io.github.outbox.order.OrderServiceApplication;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The order service started as in production - its own application class and configuration - over the shared
 * Postgres and Kafka. Nothing of the service is replaced; only the timings are shortened for a test run.
 */
@SpringBootTest(classes = OrderServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Spring Boot turns metrics export off in tests; the service is started here as in production, metrics included.
@AutoConfigureObservability
@Testcontainers
@RequiresDocker
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class OrderServiceEndToEndTestBase {

    static final String TOPIC = "order-events";

    protected final OrderApi api;
    protected final OrderEventsProbe events;

    protected OrderServiceEndToEndTestBase(int port) {
        this.api = new OrderApi(port);
        this.events = new OrderEventsProbe(OrderEndToEndContainers.KAFKA.getBootstrapServers(), TOPIC);
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OrderEndToEndContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", OrderEndToEndContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", OrderEndToEndContainers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", OrderEndToEndContainers.KAFKA::getBootstrapServers);
        registry.add("app.kafka.order-events.name", () -> TOPIC);
        registry.add("oncebox.outbox.defaults.polling.initial-delay", () -> "500ms");
        // A send to a broker that is away fails within seconds instead of the producer's two minutes.
        registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "10000");
        registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "5000");
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "5000");
        registry.add("oncebox.outbox.sender.emergency-timeout", () -> "15s");
    }

    @AfterEach
    void closeProbe() {
        events.close();
    }

    // E2E state is never cleaned, so every case works with a customer of its own.
    static long newCustomerId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    static String orderBody(long customerId) {
        return """
                {"customerId": %d, "items": [
                    {"productId": "sku-1", "quantity": 2, "price": 10.50},
                    {"productId": "sku-2", "quantity": 1, "price": 3.00}
                ]}
                """.formatted(customerId);
    }
}
