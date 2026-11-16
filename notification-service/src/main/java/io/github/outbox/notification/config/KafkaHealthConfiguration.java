package io.github.outbox.notification.config;

import io.github.outbox.notification.health.KafkaHealthIndicator;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class KafkaHealthConfiguration {

    // One client for every check, closed with the context.
    @Bean(destroyMethod = "close")
    public Admin kafkaHealthAdmin(KafkaAdmin kafkaAdmin) {
        return Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    // The bean name is the component's name under /actuator/health.
    @Bean
    public KafkaHealthIndicator kafka(Admin kafkaHealthAdmin,
                                      @Value("${app.kafka.health-timeout:3s}") Duration timeout) {
        return new KafkaHealthIndicator(kafkaHealthAdmin, timeout);
    }
}
