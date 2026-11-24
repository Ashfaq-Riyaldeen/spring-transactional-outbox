package io.github.outbox.order.e2e;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * One Postgres and one Kafka for every end-to-end test of the JVM: the Spring context is cached across classes and
 * keeps pointing at them, so they live as long as the JVM rather than as one class.
 */
final class OrderEndToEndContainers {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders")
            .withUsername("orders")
            .withPassword("orders");

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    static {
        if (Docker.availableOrCi()) {
            POSTGRES.start();
            KAFKA.start();
        }
    }

    private OrderEndToEndContainers() {
    }

    static void pauseKafka() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    static void unpauseKafka() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
}
