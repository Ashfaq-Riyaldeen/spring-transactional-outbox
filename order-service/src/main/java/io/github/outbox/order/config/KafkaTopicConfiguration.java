package io.github.outbox.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderEventsTopicProperties.class)
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic orderEventsTopic(OrderEventsTopicProperties properties) {
        return TopicBuilder.name(properties.name())
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }
}
