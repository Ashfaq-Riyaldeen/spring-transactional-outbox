package io.github.outbox.notification.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/**
 * Plays the order service: publishes order events the way its outbox does - the payload as JSON, the event id,
 * type and payload class in the oncebox headers. A header given as {@code null} is left out.
 */
final class OrderEventsPublisher implements AutoCloseable {

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    OrderEventsPublisher(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        this.producer = new KafkaProducer<>(properties);
        this.topic = topic;
    }

    void publish(UUID eventId, String eventType, Object event) {
        try {
            publishRaw(eventId == null ? null : eventId.toString(), eventType, event.getClass().getName(),
                    mapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("Event could not be published", e);
        }
    }

    void publishRaw(String eventId, String eventType, String payloadType, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        header(record, "oncebox_event_id", eventId);
        header(record, "oncebox_event_type", eventType);
        header(record, "oncebox_event_payload_type", payloadType);
        try {
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("Event could not be published", e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }

    private static void header(ProducerRecord<String, String> record, String key, String value) {
        if (value != null) {
            record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
