package io.github.outbox.notification.e2e;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Reads the dead letter topic from the start under a group of its own.
 */
final class DeadLetterProbe implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final List<String> received = new ArrayList<>();

    DeadLetterProbe(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-dlt-probe-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        this.consumer = new KafkaConsumer<>(properties);
        this.consumer.subscribe(List.of(topic));
    }

    /**
     * Polls once and returns the values of every dead letter received so far that contain the marker.
     */
    List<String> containing(String marker) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
            received.add(record.value());
        }
        return received.stream().filter(value -> value != null && value.contains(marker)).toList();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
