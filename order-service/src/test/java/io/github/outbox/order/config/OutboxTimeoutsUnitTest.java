package io.github.outbox.order.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Reads the shipped application.yml: the producer timeouts and the outbox thresholds only work together.
class OutboxTimeoutsUnitTest {

    private static Binder binder;

    @BeforeAll
    static void loadConfiguration() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));
        binder = new Binder(ConfigurationPropertySources.get(environment));
    }

    @Test
    @DisplayName("UT configuration when a send times out should fail it before the emergency timeout gives up on it")
    void configuration_whenSendTimesOut_shouldFailItBeforeTheEmergencyTimeoutGivesUpOnIt() {
        // given
        Duration deliveryTimeout = Duration.ofMillis(producerProperty("delivery.timeout.ms"));
        Duration emergencyTimeout = duration("oncebox.outbox.sender.emergency-timeout");

        // when / then
        assertThat(deliveryTimeout).isLessThan(emergencyTimeout);
    }

    @Test
    @DisplayName("UT configuration when every send of a batch blocks should still finish below the stuck threshold")
    void configuration_whenEverySendOfBatchBlocks_shouldStillFinishBelowTheStuckThreshold() {
        // given
        Duration maxBlock = Duration.ofMillis(producerProperty("max.block.ms"));
        Duration emergencyTimeout = duration("oncebox.outbox.sender.emergency-timeout");
        Duration stuckThreshold = duration("oncebox.outbox.stuck-recovery.max-batch-processing-time");
        Map<String, Integer> batchSizes = binder.bind("oncebox.outbox.events",
                        Bindable.mapOf(String.class, EventBatch.class))
                .get()
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().batchSize()));

        // when / then
        assertThat(batchSizes).containsOnlyKeys("order-created", "order-cancelled");
        batchSizes.forEach((eventType, batchSize) -> assertThat(maxBlock.multipliedBy(batchSize).plus(emergencyTimeout))
                .as(eventType)
                .isLessThan(stuckThreshold));
    }

    private static long producerProperty(String name) {
        return binder.bind("spring.kafka.producer.properties", Bindable.mapOf(String.class, String.class))
                .map(properties -> Long.parseLong(properties.get(name)))
                .orElseThrow(() -> new IllegalStateException(name + " is not configured"));
    }

    private static Duration duration(String name) {
        return binder.bind(name, Duration.class).orElseThrow(() -> new IllegalStateException(name + " is not configured"));
    }

    record EventBatch(int batchSize) {
    }
}
