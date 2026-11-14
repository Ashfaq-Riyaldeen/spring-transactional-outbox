package io.github.outbox.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaErrorHandlingConfigurationIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaErrorHandlingConfiguration.class)
            .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
            .withPropertyValues(
                    "app.kafka.order-events-topic=order-events",
                    "app.kafka.retries=3",
                    "app.kafka.retry-interval=1s"
            );

    @Test
    @DisplayName("IT context when the consumer properties are set should register the error handler")
    void context_whenConsumerPropertiesAreSet_shouldRegisterTheErrorHandler() {
        // when / then
        runner.run(context -> assertThat(context).hasSingleBean(DefaultErrorHandler.class));
    }

    @Test
    @DisplayName("IT context when the consumer properties are set should declare the dead letter topic")
    void context_whenConsumerPropertiesAreSet_shouldDeclareTheDeadLetterTopic() {
        // when / then
        runner.run(context -> {
            NewTopic topic = context.getBean(NewTopic.class);
            assertThat(topic.name()).isEqualTo("order-events-dlt");
            assertThat(topic.numPartitions()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("IT context when retries are negative should fail naming the property")
    void context_whenRetriesAreNegative_shouldFailNamingTheProperty() {
        // when / then
        runner.withPropertyValues("app.kafka.retries=-1")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("retries"));
    }

    @Test
    @DisplayName("IT context when the topic is blank should fail naming the property")
    void context_whenTopicIsBlank_shouldFailNamingTheProperty() {
        // when / then
        runner.withPropertyValues("app.kafka.order-events-topic= ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("orderEventsTopic"));
    }
}
