package io.github.outbox.order.bulkhead;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreationBulkheadMultiThreadTest {

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT acquire() when two holders keep both slots should refuse a third caller until one of them leaves")
    void acquire_whenTwoHoldersKeepBothSlots_shouldRefuseThirdCallerUntilOneLeaves() throws Exception {
        // given
        OrderCreationBulkhead tested = new OrderCreationBulkhead(new BulkheadProperties(2, Duration.ZERO));
        CountDownLatch holding = new CountDownLatch(2);
        CountDownLatch leave = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2,
                Thread.ofPlatform().name("holder-", 0).factory())) {
            List<Future<?>> holders = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                holders.add(pool.submit(() -> {
                    tested.acquire();
                    try {
                        holding.countDown();
                        assertThat(leave.await(15, TimeUnit.SECONDS)).as("holder told to leave").isTrue();
                    } finally {
                        tested.release();
                    }
                    return null;
                }));
            }
            assertThat(holding.await(15, TimeUnit.SECONDS)).as("both holders inside").isTrue();

            // when / then
            assertThatThrownBy(tested::acquire).as("third caller while both slots are held")
                    .isInstanceOf(BulkheadFullException.class);
            leave.countDown();
            for (Future<?> holder : holders) {
                holder.get(15, TimeUnit.SECONDS);
            }
        }
        tested.acquire();
        assertThat(tested.availableSlots()).as("slots left after the third caller got in").isEqualTo(1);
    }

    @RepeatedTest(20)
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT acquire() when many callers burst in should never let more than the limit run at once")
    void acquire_whenManyCallersBurstIn_shouldNeverLetMoreThanTheLimitRunAtOnce() throws Exception {
        // given
        int threads = 24;
        int limit = 4;
        OrderCreationBulkhead tested =
                new OrderCreationBulkhead(new BulkheadProperties(limit, Duration.ofSeconds(10)));
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger mostInside = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when
        try (ExecutorService pool = Executors.newFixedThreadPool(threads,
                Thread.ofPlatform().name("bulkhead-caller-", 0).factory())) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(15, TimeUnit.SECONDS)).as("start gate opened").isTrue();
                    tested.acquire();
                    try {
                        mostInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        for (int spin = 0; spin < 1_000; spin++) {
                            Thread.onSpinWait();
                        }
                        inside.decrementAndGet();
                        completed.incrementAndGet();
                    } finally {
                        tested.release();
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

        // then
        assertThat(mostInside.get()).as("most callers inside at once").isLessThanOrEqualTo(limit);
        assertThat(completed.get()).as("callers that got through").isEqualTo(threads);
        assertThat(tested.availableSlots()).as("slots after everyone left").isEqualTo(limit);
    }
}
