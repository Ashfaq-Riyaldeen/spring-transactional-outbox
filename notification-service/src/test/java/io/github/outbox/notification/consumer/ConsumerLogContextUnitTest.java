package io.github.outbox.notification.consumer;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerLogContextUnitTest {

    private static final OrderEvent EVENT =
            new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"), "checkout-1", null);
    private static final String EVENT_ID = "0195f2a4-3c1e-7d2a-9b4c-1a2b3c4d5e6f";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("UT of() when the message has its headers should name the event, order, customer and correlation")
    void of_whenMessageHasItsHeaders_shouldNameEventOrderCustomerAndCorrelation() {
        // given
        Message<OrderEvent> message = MessageBuilder.withPayload(EVENT)
                .setHeader("oncebox_event_id", EVENT_ID)
                .setHeader("oncebox_event_type", "order-cancelled")
                .build();

        // when
        ConsumerLogContext.of(message);

        // then
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eventId", EVENT_ID,
                "eventType", "order-cancelled",
                "orderId", "7",
                "customerId", "42",
                "correlationId", "checkout-1"
        ));
    }

    @Test
    @DisplayName("UT of() when the event carries a trace parent should name the trace id of the causing request")
    void of_whenEventCarriesTraceParent_shouldNameTheTraceIdOfTheCausingRequest() {
        // given
        Message<OrderEvent> message = MessageBuilder.withPayload(tracedEvent(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).build();

        // when
        ConsumerLogContext.of(message);

        // then
        assertThat(MDC.get(ConsumerLogContext.ORIGIN_TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("UT of() when the event carries no trace parent should leave the origin trace id out")
    void of_whenEventCarriesNoTraceParent_shouldLeaveTheOriginTraceIdOut() {
        // when
        ConsumerLogContext.of(MessageBuilder.withPayload(EVENT).build());

        // then
        assertThat(MDC.get(ConsumerLogContext.ORIGIN_TRACE_ID)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00-4bf92f3577b34da6-00f067aa0ba902b7-01",
            "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra"
    })
    @DisplayName("UT of() when the trace parent is not a W3C traceparent should leave the origin trace id out")
    void of_whenTraceParentIsNotW3cTraceparent_shouldLeaveTheOriginTraceIdOut(String traceParent) {
        // when
        ConsumerLogContext.of(MessageBuilder.withPayload(tracedEvent(traceParent)).build());

        // then
        assertThat(MDC.get(ConsumerLogContext.ORIGIN_TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("UT of() when headers come as bytes should log them as text")
    void of_whenHeadersComeAsBytes_shouldLogThemAsText() {
        // given
        Message<OrderEvent> message = MessageBuilder.withPayload(EVENT)
                .setHeader("oncebox_event_id", EVENT_ID.getBytes(StandardCharsets.UTF_8))
                .build();

        // when
        ConsumerLogContext.of(message);

        // then
        assertThat(MDC.get(ConsumerLogContext.EVENT_ID)).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("UT of() when a header is missing should leave it out instead of logging it empty")
    void of_whenHeaderIsMissing_shouldLeaveItOut() {
        // when
        ConsumerLogContext.of(MessageBuilder.withPayload(EVENT).build());

        // then
        assertThat(MDC.get(ConsumerLogContext.EVENT_ID)).isNull();
        assertThat(MDC.get(ConsumerLogContext.EVENT_TYPE)).isNull();
        assertThat(MDC.get(ConsumerLogContext.ORDER_ID)).isEqualTo("7");
    }

    @Test
    @DisplayName("UT close() when the event is handled should remove its keys and keep those of others")
    void close_whenEventIsHandled_shouldRemoveItsKeysAndKeepThoseOfOthers() {
        // given
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        ConsumerLogContext tested = ConsumerLogContext.of(MessageBuilder.withPayload(EVENT)
                .setHeader("oncebox_event_id", EVENT_ID)
                .build());

        // when
        tested.close();

        // then
        assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    private static OrderEvent tracedEvent(String traceParent) {
        return new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"), "checkout-1", traceParent);
    }
}
