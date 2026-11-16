package io.github.outbox.order.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentTraceParentUnitTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext context;

    @InjectMocks
    private CurrentTraceParent tested;

    @Test
    @DisplayName("UT get() when the current span is sampled should return its traceparent with the sampled flag")
    void get_whenCurrentSpanIsSampled_shouldReturnItsTraceparentWithSampledFlag() {
        // given
        givenCurrentSpan(TRACE_ID, SPAN_ID);
        when(context.sampled()).thenReturn(true);

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    @DisplayName("UT get() when the current span is not sampled should return its traceparent without the flag")
    void get_whenCurrentSpanIsNotSampled_shouldReturnItsTraceparentWithoutTheFlag() {
        // given
        givenCurrentSpan(TRACE_ID, SPAN_ID);
        when(context.sampled()).thenReturn(false);

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
    }

    @Test
    @DisplayName("UT get() when the sampling decision is unknown should return the traceparent as not sampled")
    void get_whenSamplingDecisionIsUnknown_shouldReturnTheTraceparentAsNotSampled() {
        // given
        givenCurrentSpan(TRACE_ID, SPAN_ID);
        when(context.sampled()).thenReturn(null);

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
    }

    @Test
    @DisplayName("UT get() when there is no current span should return null")
    void get_whenThereIsNoCurrentSpan_shouldReturnNull() {
        // given
        when(tracer.currentSpan()).thenReturn(null);

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isNull();
    }

    @Test
    @DisplayName("UT get() when the current span has no trace id, as with tracing off, should return null")
    void get_whenCurrentSpanHasNoTraceId_shouldReturnNull() {
        // given
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("");

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isNull();
    }

    @Test
    @DisplayName("UT get() when the current span has no span id should return null")
    void get_whenCurrentSpanHasNoSpanId_shouldReturnNull() {
        // given
        givenCurrentSpan(TRACE_ID, "");

        // when
        String traceParent = tested.get();

        // then
        assertThat(traceParent).isNull();
    }

    private void givenCurrentSpan(String traceId, String spanId) {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn(traceId);
        when(context.spanId()).thenReturn(spanId);
    }
}
