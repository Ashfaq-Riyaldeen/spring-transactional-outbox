package io.github.outbox.order.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

// Bursts of callers released together against a frozen clock: no limit may let more through than its capacity.
class OrderCreationRateLimiterMultiThreadTest {

    private static final int THREADS = 32;
    private static final RateLimitProperties.Limit ROOMY = new RateLimitProperties.Limit(1000, Duration.ofMinutes(1));

    @RepeatedTest(20)
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT acquire() when callers of many addresses race for the global limit should pass its capacity")
    void acquire_whenCallersRaceForGlobalLimit_shouldLetExactlyItsCapacityThrough() throws Exception {
        // given
        OrderCreationRateLimiter tested = limiter(limit(10), ROOMY, ROOMY);

        // when
        Outcome outcome = burst(index -> tested.acquire("10.0.0." + index));

        // then
        assertThat(outcome.passed()).as("requests let through").isEqualTo(10);
        assertThat(outcome.refused()).as("requests refused").isEqualTo(THREADS - 10);
    }

    @RepeatedTest(20)
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT acquire() when callers of one address race for its limit should let its capacity through")
    void acquire_whenCallersOfOneAddressRace_shouldLetExactlyItsCapacityThrough() throws Exception {
        // given
        OrderCreationRateLimiter tested = limiter(ROOMY, limit(5), ROOMY);

        // when
        Outcome outcome = burst(index -> tested.acquire("10.0.0.1"));

        // then
        assertThat(outcome.passed()).as("requests let through").isEqualTo(5);
        assertThat(outcome.refused()).as("requests refused").isEqualTo(THREADS - 5);
    }

    @RepeatedTest(20)
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT acquireForCustomer() when orders of one customer race should let its capacity through")
    void acquireForCustomer_whenOrdersOfOneCustomerRace_shouldLetExactlyItsCapacityThrough() throws Exception {
        // given
        OrderCreationRateLimiter tested = limiter(ROOMY, ROOMY, limit(3));

        // when
        Outcome outcome = burst(index -> tested.acquireForCustomer(42));

        // then
        assertThat(outcome.passed()).as("orders let through").isEqualTo(3);
        assertThat(outcome.refused()).as("orders refused").isEqualTo(THREADS - 3);
    }

    // Every worker parks behind one gate, so they really meet at the bucket; a refusal is the expected outcome and is
    // counted, anything else fails the worker's future and the test.
    private static Outcome burst(IntConsumer call) throws Exception {
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS,
                Thread.ofPlatform().name("rate-limit-caller-", 0).factory())) {
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(15, TimeUnit.SECONDS)).as("start gate opened").isTrue();
                    try {
                        call.accept(index);
                        passed.incrementAndGet();
                    } catch (RateLimitExceededException exception) {
                        refused.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).as("every worker at the gate").isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }
        return new Outcome(passed.get(), refused.get());
    }

    private static OrderCreationRateLimiter limiter(RateLimitProperties.Limit global, RateLimitProperties.Limit perIp,
                                                    RateLimitProperties.Limit perCustomer) {
        return new OrderCreationRateLimiter(new RateLimitProperties(global, perIp, perCustomer, 100),
                new FrozenTimeMeter());
    }

    private static RateLimitProperties.Limit limit(long capacity) {
        return new RateLimitProperties.Limit(capacity, Duration.ofMinutes(1));
    }

    private record Outcome(int passed, int refused) {
    }

    private static final class FrozenTimeMeter implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return 0;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
