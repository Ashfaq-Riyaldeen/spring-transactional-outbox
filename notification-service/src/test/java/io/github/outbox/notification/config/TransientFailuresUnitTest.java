package io.github.outbox.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

class TransientFailuresUnitTest {

    @Test
    @DisplayName("UT isTransient() when no connection could be obtained should be transient")
    void isTransient_whenNoConnectionCouldBeObtained_shouldBeTransient() {
        // when / then
        assertThat(TransientFailures.isTransient(new CannotGetJdbcConnectionException("pool exhausted"))).isTrue();
    }

    @Test
    @DisplayName("UT isTransient() when the transient cause is deep in the chain should be transient")
    void isTransient_whenTransientCauseIsDeepInTheChain_shouldBeTransient() {
        // given
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new IllegalStateException("wrapped", new SQLTransientConnectionException("timed out")));

        // when / then
        assertThat(TransientFailures.isTransient(failure)).isTrue();
    }

    @Test
    @DisplayName("UT isTransient() when a constraint is violated should not be transient")
    void isTransient_whenConstraintIsViolated_shouldNotBeTransient() {
        // when / then
        assertThat(TransientFailures.isTransient(new DataIntegrityViolationException("duplicate key"))).isFalse();
    }

    @Test
    @DisplayName("UT isTransient() when there is no failure should not be transient")
    void isTransient_whenThereIsNoFailure_shouldNotBeTransient() {
        // when / then
        assertThat(TransientFailures.isTransient(null)).isFalse();
    }
}
