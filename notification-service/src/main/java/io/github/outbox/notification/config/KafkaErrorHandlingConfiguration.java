package io.github.outbox.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * An event that keeps failing is published to the dead letter topic before its offset is committed. Without the
 * recoverer the error handler would commit it after the last retry and the event would be gone.
 * <p>
 * The handler is picked up by every listener container factory configured through Spring Boot, the inbox one
 * included. A record that cannot be converted at all is not retried.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaConsumerProperties.class)
public class KafkaErrorHandlingConfiguration {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 KafkaConsumerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                // a negative partition leaves the choice to the producer, whatever the topic's partition count
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), -1));
        return new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.retries()));
    }

    @Bean
    public NewTopic orderEventsDeadLetterTopic(KafkaConsumerProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).partitions(1).build();
    }
}
