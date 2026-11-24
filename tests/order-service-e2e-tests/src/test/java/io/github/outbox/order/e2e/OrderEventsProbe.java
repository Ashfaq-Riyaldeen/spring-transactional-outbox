package io.github.outbox.order.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Reads what the order service published to the order events topic, the way a consumer would. Each probe reads
 * the topic from the start under a group of its own; tests tell their events apart by order or customer id.
 */
final class OrderEventsProbe implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<PublishedEvent> received = new ArrayList<>();

    OrderEventsProbe(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-probe-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        this.consumer = new KafkaConsumer<>(properties);
        this.consumer.subscribe(List.of(topic));
    }

    /**
     * Polls once and returns every event received so far of the given type whose payload field has the value.
     */
    List<PublishedEvent> events(String eventType, String field, long value) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
            received.add(toEvent(record));
        }
        return received.stream()
                .filter(event -> eventType.equals(event.headers().get("oncebox_event_type")))
                .filter(event -> event.payload().path(field).asLong() == value)
                .toList();
    }

    @Override
    public void close() {
        consumer.close();
    }

    private PublishedEvent toEvent(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        try {
            return new PublishedEvent(headers, mapper.readTree(record.value()));
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable event payload: " + record.value(), e);
        }
    }

    record PublishedEvent(Map<String, String> headers, JsonNode payload) {
    }
}
