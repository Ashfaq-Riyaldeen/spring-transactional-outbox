package io.github.outbox.order.logging;

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
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(((LogstashEncoder) encoder).getCustomFields()).contains("\"application\":\"order-service\"");
    }

    @Test
    @DisplayName("IT logging when the local profile is active should write plain text")
    void logging_whenLocalProfileIsActive_shouldWritePlainText() {
        // when
        initialize("local");

        // then
        assertThat(rootConsoleEncoder()).isInstanceOf(PatternLayoutEncoder.class);
    }

    @Test
    @DisplayName("IT logging when the json-file profile is active should also write JSON lines to the file")
    void logging_whenJsonFileProfileIsActive_shouldAlsoWriteJsonLinesToTheFile(@TempDir Path dir) throws IOException {
        // given
        initialize(dir, "json-file");

        // when
        LoggerFactory.getLogger(getClass()).info("order 7 created");

        // then
        assertThat(rootAppenders()).hasSize(2);
        List<String> lines = Files.readAllLines(dir.resolve("order-service.json"));
        assertThat(lines).singleElement().satisfies(line -> assertThat(line)
                .startsWith("{")
                .contains("\"message\":\"order 7 created\"")
                .contains("\"application\":\"order-service\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("IT logging when the local and json-file profiles are active should keep the console plain")
    void logging_whenLocalAndJsonFileProfilesAreActive_shouldKeepTheConsolePlain(@TempDir Path dir) {
        // when
        initialize(dir, "local", "json-file");

        // then
        assertThat(rootAppenders()).hasSize(2);
        assertThat(Files.exists(dir.resolve("order-service.json"))).as("the JSON file").isTrue();
        assertThat(rootAppenders()).filteredOn(ConsoleAppender.class::isInstance).singleElement()
                .satisfies(appender -> assertThat(((ConsoleAppender<ILoggingEvent>) appender).getEncoder())
                        .isInstanceOf(PatternLayoutEncoder.class));
    }

    private void initialize(String... profiles) {
        initialize(null, profiles);
    }

    private void initialize(Path jsonFileDir, String... profiles) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "order-service");
        if (jsonFileDir != null) {
            environment.setProperty("app.logging.json-file.dir", jsonFileDir.toString());
        }
        environment.setActiveProfiles(profiles);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    @SuppressWarnings("unchecked")
    private static Encoder<ILoggingEvent> rootConsoleEncoder() {
        List<Appender<ILoggingEvent>> appenders = rootAppenders();
        assertThat(appenders).hasSize(1).first().isInstanceOf(ConsoleAppender.class);
        return ((ConsoleAppender<ILoggingEvent>) appenders.get(0)).getEncoder();
    }

    private static List<Appender<ILoggingEvent>> rootAppenders() {
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(appenders::add);
        return appenders;
    }
}
