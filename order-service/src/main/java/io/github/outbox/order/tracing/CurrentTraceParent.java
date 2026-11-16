package io.github.outbox.order.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The trace context of the request being handled, in the form it is written into an event: an event leaves the
 * service later, from the outbox poller, where the request's span is long gone.
 */
@Component
@RequiredArgsConstructor
public class CurrentTraceParent {

    private static final String VERSION = "00";
    private static final String SAMPLED = "01";
    private static final String NOT_SAMPLED = "00";

    private final Tracer tracer;

    /**
     * @return the W3C {@code traceparent} of the current span, or {@code null} when the calling thread is not in a
     *         span or tracing is switched off
     */
    public String get() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        if (context.traceId().isEmpty() || context.spanId().isEmpty()) {
            return null;
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? SAMPLED : NOT_SAMPLED;
        return String.join("-", VERSION, context.traceId(), context.spanId(), flags);
    }
}
