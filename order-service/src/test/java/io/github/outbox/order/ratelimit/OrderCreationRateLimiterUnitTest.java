package io.github.outbox.order.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreationRateLimiterUnitTest {

    @Test
    @DisplayName("UT acquire() when the global capacity is not used up should let every request through")
    void acquire_whenGlobalCapacityIsNotUsedUp_shouldLetEveryRequestThrough() {
        // given
        OrderCreationRateLimiter tested =
                new OrderCreationRateLimiter(global(3, Duration.ofMinutes(1)), new TestTimeMeter());

        // when / then
        assertThatCode(() -> {
            tested.acquire();
            tested.acquire();
            tested.acquire();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquire() when the global capacity is used up should refuse with the wait until the next request")
    void acquire_whenGlobalCapacityIsUsedUp_shouldRefuseWithTheWaitUntilNextRequest() {
        // given
        OrderCreationRateLimiter tested =
                new OrderCreationRateLimiter(global(2, Duration.ofMinutes(1)), new TestTimeMeter());
        tested.acquire();
        tested.acquire();

        // when / then
        assertThatThrownBy(tested::acquire)
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("UT acquire() when the wait is shorter than a second should ask to retry after one second")
    void acquire_whenWaitIsShorterThanSecond_shouldAskToRetryAfterOneSecond() {
        // given
        OrderCreationRateLimiter tested =
                new OrderCreationRateLimiter(global(10, Duration.ofSeconds(1)), new TestTimeMeter());
        for (int i = 0; i < 10; i++) {
            tested.acquire();
        }

        // when / then
        assertThatThrownBy(tested::acquire)
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("UT acquire() when the period has passed after a refusal should let the request through again")
    void acquire_whenPeriodHasPassedAfterRefusal_shouldLetRequestThroughAgain() {
        // given
        TestTimeMeter time = new TestTimeMeter();
        OrderCreationRateLimiter tested = new OrderCreationRateLimiter(global(1, Duration.ofMinutes(1)), time);
        tested.acquire();
        assertThatThrownBy(tested::acquire).isInstanceOf(RateLimitExceededException.class);

        // when
        time.advance(Duration.ofMinutes(1));

        // then
        assertThatCode(tested::acquire).doesNotThrowAnyException();
    }

    private static RateLimitProperties global(long capacity, Duration period) {
        return new RateLimitProperties(new RateLimitProperties.Limit(capacity, period));
    }

    private static final class TestTimeMeter implements TimeMeter {

        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
