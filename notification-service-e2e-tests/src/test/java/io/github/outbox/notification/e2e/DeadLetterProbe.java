package io.github.outbox.notification.e2e;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
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
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

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
        return recordsContaining(marker).stream().map(ConsumerRecord::value).toList();
    }

    /**
     * Polls once and returns, for every dead letter received so far that contains the marker, how long after the
     * original record it was published - measured between the two records' timestamps, not by when the probe
     * read it.
     */
    List<Duration> delaysOf(String marker) {
        return recordsContaining(marker).stream()
                .map(record -> Duration.ofMillis(record.timestamp() - originalTimestamp(record)))
                .toList();
    }

    private List<ConsumerRecord<String, String>> recordsContaining(String marker) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
            received.add(record);
        }
        return received.stream().filter(record -> record.value() != null && record.value().contains(marker)).toList();
    }

    private static long originalTimestamp(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP);
        if (header == null) {
            throw new IllegalStateException("Dead letter without the original timestamp: " + record.value());
        }
        return ByteBuffer.wrap(header.value()).getLong();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
