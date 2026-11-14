package io.github.outbox.notification.e2e;

import org.awaitility.core.ConditionFactory;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * The waits of this module, with their timeouts defined once.
 */
final class Waits {

    // Long enough that a record moved aside after a retry is told apart from one moved aside at once.
    static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);
    static final Duration DELIVERY = Duration.ofSeconds(30);
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
