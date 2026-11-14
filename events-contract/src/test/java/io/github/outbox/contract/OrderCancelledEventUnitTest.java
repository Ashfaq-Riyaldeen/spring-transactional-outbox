package io.github.outbox.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCancelledEventUnitTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-14T10:15:30Z");

    @Test
    @DisplayName("UT constructor() when occurredAt is null should throw NullPointerException")
    void constructor_whenOccurredAtIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCancelledEvent(1L, 42L, null, "c-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when correlationId is null should throw NullPointerException")
    void constructor_whenCorrelationIdIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCancelledEvent(1L, 42L, OCCURRED_AT, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("correlationId cannot be null");
    }

    @Test
    @DisplayName("UT JSON round trip when trace parent is present should read back an equal event")
    void jsonRoundTrip_whenTraceParentIsPresent_shouldReadBackAnEqualEvent() throws Exception {
        // given
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        OrderCancelledEvent tested = new OrderCancelledEvent(
                7L,
                42L,
                OCCURRED_AT,
                "c-1",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        // when
        OrderCancelledEvent read = mapper.readValue(mapper.writeValueAsString(tested), OrderCancelledEvent.class);

        // then
        assertThat(read).isEqualTo(tested);
    }
}
