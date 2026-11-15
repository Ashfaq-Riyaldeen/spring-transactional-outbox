package io.github.outbox.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.outbox.notification.api.NotificationProblems.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// The Problem record documents the error body; these cases hold its components to the keys the API really sends.
class ProblemUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30Z");

    @Test
    @DisplayName("UT Problem when compared with a serialised validation problem should have exactly its keys")
    void components_whenComparedWithValidationProblem_shouldBeExactlyItsKeys() throws Exception {
        // given
        ProblemDetail problem = NotificationProblems.validationFailed(
                List.of(new FieldViolation("orderId", "orderId must be positive")),
                "/api/v1/notifications",
                NOW
        );

        // when
        Set<String> keys = serialisedKeys(problem);

        // then
        assertThat(keys).containsExactlyInAnyOrderElementsOf(componentNames(Problem.class));
    }

    @Test
    @DisplayName("UT Problem when compared with a serialised internal error should differ only by errors")
    void components_whenComparedWithInternalError_shouldDifferOnlyByErrors() throws Exception {
        // given
        ProblemDetail problem = NotificationProblems.internalError("/api/v1/notifications", NOW);

        // when
        Set<String> keys = serialisedKeys(problem);

        // then
        Set<String> expected = componentNames(Problem.class);
        expected.remove("errors");
        assertThat(keys).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("UT FieldViolation when serialised should have exactly its components as keys")
    void components_whenFieldViolationIsSerialised_shouldBeExactlyItsKeys() throws Exception {
        // given
        FieldViolation violation = new FieldViolation("orderId", "orderId must be positive");

        // when
        Set<String> keys = keysOf(mapper().valueToTree(violation));

        // then
        assertThat(keys).containsExactlyInAnyOrderElementsOf(componentNames(FieldViolation.class));
    }

    // Built as Spring Boot builds it: the builder applies the mixin that lifts ProblemDetail properties to the top.
    private static ObjectMapper mapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }

    private static Set<String> serialisedKeys(ProblemDetail problem) throws Exception {
        ObjectMapper mapper = mapper();
        return keysOf(mapper.readTree(mapper.writeValueAsString(problem)));
    }

    private static Set<String> keysOf(JsonNode node) {
        Set<String> keys = new HashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static Set<String> componentNames(Class<? extends Record> type) {
        Set<String> names = new HashSet<>();
        Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).forEach(names::add);
        return names;
    }
}
