package io.github.outbox.notification.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Initialises logging from the service's logback-spring.xml the way Spring Boot does, for one profile at a time.
class LogbackConfigurationIntegrationTest {

    private final LogbackLoggingSystem loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());

    @AfterEach
    void restoreDefaultLogging() {
        initialize();
    }

    @Test
    @DisplayName("IT logging when no profile is active should write JSON with the application name")
    void logging_whenNoProfileIsActive_shouldWriteJsonWithApplicationName() {
        // when
        initialize();

        // then
        Encoder<ILoggingEvent> encoder = rootConsoleEncoder();
        assertThat(encoder).isInstanceOf(LogstashEncoder.class);
        assertThat(((LogstashEncoder) encoder).getCustomFields())
                .contains("\"application\":\"notification-service\"");
    }

    @Test
    @DisplayName("IT logging when the local profile is active should write plain text")
    void logging_whenLocalProfileIsActive_shouldWritePlainText() {
        // when
        initialize("local");

        // then
        assertThat(rootConsoleEncoder()).isInstanceOf(PatternLayoutEncoder.class);
    }

    private void initialize(String... profiles) {
        MockEnvironment environment =
                new MockEnvironment().withProperty("spring.application.name", "notification-service");
        environment.setActiveProfiles(profiles);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    @SuppressWarnings("unchecked")
    private static Encoder<ILoggingEvent> rootConsoleEncoder() {
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(appenders::add);
        assertThat(appenders).hasSize(1).first().isInstanceOf(ConsoleAppender.class);
        return ((ConsoleAppender<ILoggingEvent>) appenders.get(0)).getEncoder();
    }
}
