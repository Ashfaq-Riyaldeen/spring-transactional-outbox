package io.github.outbox.order.e2e;

import org.awaitility.core.ConditionFactory;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * The waits of this module, with their timeouts defined once.
 */
final class Waits {

    // Long enough for a poller that backs off while Kafka is away to come back and send.
    static final Duration DELIVERY = Duration.ofSeconds(90);
    static final Duration QUIET = Duration.ofSeconds(3);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private Waits() {
    }

    static ConditionFactory awaitDelivery() {
        return await().atMost(DELIVERY).pollInterval(POLL_INTERVAL).pollInSameThread();
    }

    static ConditionFactory awaitQuiet() {
        return await().during(QUIET).atMost(QUIET.plusSeconds(5)).pollInterval(POLL_INTERVAL).pollInSameThread();
    }
}
