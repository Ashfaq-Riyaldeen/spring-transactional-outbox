package io.github.outbox.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaErrorHandlingConfigurationUnitTest {

    private static final KafkaConsumerProperties PROPERTIES = new KafkaConsumerProperties("order-events", 3,
            Duration.ofSeconds(1), new KafkaConsumerProperties.TransientRetry(Duration.ofMillis(500),
            Duration.ofSeconds(30)));

    @Test
    @DisplayName("UT backOffFor() when the listener failed on a transient cause should retry without a limit")
    void backOffFor_whenListenerFailedOnTransientCause_shouldRetryWithoutLimit() {
        // given
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new CannotCreateTransactionException("no connection"));

        // when
        BackOff backOff = KafkaErrorHandlingConfiguration.backOffFor(failure, PROPERTIES);

        // then
        assertThat(backOff).isInstanceOfSatisfying(ExponentialBackOff.class, exponential -> {
            assertThat(exponential.getInitialInterval()).isEqualTo(500L);
            assertThat(exponential.getMaxInterval()).isEqualTo(30_000L);
            assertThat(exponential.getMaxElapsedTime()).isEqualTo(Long.MAX_VALUE);
        });
    }

    @Test
    @DisplayName("UT backOffFor() when the failure repeats the same way should keep the fixed retries")
    void backOffFor_whenFailureRepeatsTheSameWay_shouldKeepTheFixedRetries() {
        // given
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new IllegalStateException("bad payload"));

        // when
        BackOff backOff = KafkaErrorHandlingConfiguration.backOffFor(failure, PROPERTIES);

        // then
        assertThat(backOff).isNull();
    }
}
