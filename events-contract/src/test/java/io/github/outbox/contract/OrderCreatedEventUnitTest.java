package io.github.outbox.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreatedEventUnitTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-14T10:15:30Z");
    private static final OrderLine LINE = new OrderLine("sku-1", 2, new BigDecimal("10.50"));

    @Test
    @DisplayName("UT constructor() when lines is null should throw NullPointerException")
    void constructor_whenLinesIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCreatedEvent(1L, 42L, null, BigDecimal.TEN, OCCURRED_AT, "c-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lines cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when total is null should throw NullPointerException")
    void constructor_whenTotalIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCreatedEvent(1L, 42L, List.of(LINE), null, OCCURRED_AT, "c-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("total cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when occurredAt is null should throw NullPointerException")
    void constructor_whenOccurredAtIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCreatedEvent(1L, 42L, List.of(LINE), BigDecimal.TEN, null, "c-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when correlationId is null should throw NullPointerException")
    void constructor_whenCorrelationIdIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderCreatedEvent(1L, 42L, List.of(LINE), BigDecimal.TEN, OCCURRED_AT, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("correlationId cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when lines is empty should throw IllegalArgumentException")
    void constructor_whenLinesIsEmpty_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new OrderCreatedEvent(1L, 42L, List.of(), BigDecimal.TEN, OCCURRED_AT, "c-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lines cannot be empty");
    }

    @Test
    @DisplayName("UT constructor() when the passed list changes afterwards should keep the lines it was built with")
    void constructor_whenPassedListChangesAfterwards_shouldKeepTheLinesItWasBuiltWith() {
        // given
        List<OrderLine> lines = new ArrayList<>(List.of(LINE));
        OrderCreatedEvent tested = new OrderCreatedEvent(1L, 42L, lines, BigDecimal.TEN, OCCURRED_AT, "c-1", null);

        // when
        lines.add(new OrderLine("sku-2", 1, BigDecimal.ONE));

        // then
        assertThat(tested.lines()).containsExactly(LINE);
        assertThatThrownBy(() -> tested.lines().add(LINE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UT JSON round trip when trace parent is absent should read back an equal event")
    void jsonRoundTrip_whenTraceParentIsAbsent_shouldReadBackAnEqualEvent() throws Exception {
        // given
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        OrderCreatedEvent tested = new OrderCreatedEvent(
                7L,
                42L,
                List.of(LINE),
                new BigDecimal("21.00"),
                OCCURRED_AT,
                "c-1",
                null
        );

        // when
        OrderCreatedEvent read = mapper.readValue(mapper.writeValueAsString(tested), OrderCreatedEvent.class);

        // then
        assertThat(read).isEqualTo(tested);
    }
}
