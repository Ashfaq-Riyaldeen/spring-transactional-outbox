package io.github.outbox.order.ratelimit;

import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreationRateLimiterUnitTest {

    private static final RateLimitProperties.Limit ROOMY = new RateLimitProperties.Limit(1000, Duration.ofMinutes(1));

    @Test
    @DisplayName("UT acquire() when no limit is used up should let every request through")
    void acquire_whenNoLimitIsUsedUp_shouldLetEveryRequestThrough() {
        // given
        OrderCreationRateLimiter tested = limiter(limit(3), ROOMY, ROOMY);

        // when / then
        assertThatCode(() -> {
            tested.acquire("10.0.0.1");
            tested.acquire("10.0.0.2");
            tested.acquire("10.0.0.3");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquire() when the global limit is used up should refuse any address with the wait")
    void acquire_whenGlobalLimitIsUsedUp_shouldRefuseAnyAddressWithTheWait() {
        // given
        OrderCreationRateLimiter tested = limiter(limit(2), ROOMY, ROOMY);
        tested.acquire("10.0.0.1");
        tested.acquire("10.0.0.2");

        // when / then
        assertThatThrownBy(() -> tested.acquire("10.0.0.3"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("Too many orders are being created, retry later");
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    @DisplayName("UT acquire() when one address uses up its limit should refuse it and leave other addresses alone")
    void acquire_whenOneAddressUsesUpItsLimit_shouldRefuseItAndLeaveOthersAlone() {
        // given
        OrderCreationRateLimiter tested = limiter(ROOMY, limit(2), ROOMY);
        tested.acquire("10.0.0.1");
        tested.acquire("10.0.0.1");

        // when / then
        assertThatThrownBy(() -> tested.acquire("10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many orders from this address, retry later");
        assertThatCode(() -> tested.acquire("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquire() when an address is refused should not take from the global limit")
    void acquire_whenAddressIsRefused_shouldNotTakeFromGlobalLimit() {
        // given
        OrderCreationRateLimiter tested = limiter(limit(2), limit(1), ROOMY);
        tested.acquire("10.0.0.1");
        assertThatThrownBy(() -> tested.acquire("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);

        // when / then
        assertThatCode(() -> tested.acquire("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquireForCustomer() when one customer uses up its limit should refuse it and leave others alone")
    void acquireForCustomer_whenOneCustomerUsesUpItsLimit_shouldRefuseItAndLeaveOthersAlone() {
        // given
        OrderCreationRateLimiter tested = limiter(ROOMY, ROOMY, limit(1));
        tested.acquireForCustomer(42);

        // when / then
        assertThatThrownBy(() -> tested.acquireForCustomer(42))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("Too many orders for customer 42, retry later");
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(1));
                });
        assertThatCode(() -> tested.acquireForCustomer(43)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT acquire() when limits refuse requests should count each refusal under the limit that refused it")
    void acquire_whenLimitsRefuseRequests_shouldCountEachRefusalUnderTheLimit() {
        // given
        MeterRegistry registry = new SimpleMeterRegistry();
        OrderCreationRateLimiter tested = new OrderCreationRateLimiter(
                properties(limit(1), limit(1), limit(1)), registry, new TestTimeMeter());
        tested.acquire("10.0.0.1");
        tested.acquireForCustomer(42);

        // when
        assertThatThrownBy(() -> tested.acquire("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> tested.acquire("10.0.0.2")).isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> tested.acquireForCustomer(42)).isInstanceOf(RateLimitExceededException.class);

        // then
        assertThat(rejected(registry, "per_ip")).isEqualTo(1);
        assertThat(rejected(registry, "global")).isEqualTo(1);
        assertThat(rejected(registry, "per_customer")).isEqualTo(1);
    }

    @Test
    @DisplayName("UT constructor() should register a zero counter for every limit")
    void constructor_whenCreated_shouldRegisterZeroCounterForEveryLimit() {
        // given
        MeterRegistry registry = new SimpleMeterRegistry();

        // when
        new OrderCreationRateLimiter(properties(ROOMY, ROOMY, ROOMY), registry, new TestTimeMeter());

        // then
        assertThat(rejected(registry, "global")).isZero();
        assertThat(rejected(registry, "per_ip")).isZero();
        assertThat(rejected(registry, "per_customer")).isZero();
    }

    @Test
    @DisplayName("UT acquire() when the wait is shorter than a second should ask to retry after one second")
    void acquire_whenWaitIsShorterThanSecond_shouldAskToRetryAfterOneSecond() {
        // given
        OrderCreationRateLimiter tested =
                limiter(new RateLimitProperties.Limit(10, Duration.ofSeconds(1)), ROOMY, ROOMY);
        for (int i = 0; i < 10; i++) {
            tested.acquire("10.0.0.1");
        }

        // when / then
        assertThatThrownBy(() -> tested.acquire("10.0.0.1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("UT acquire() when the period has passed after a refusal should let the request through again")
    void acquire_whenPeriodHasPassedAfterRefusal_shouldLetRequestThroughAgain() {
        // given
        TestTimeMeter time = new TestTimeMeter();
        OrderCreationRateLimiter tested =
                new OrderCreationRateLimiter(properties(limit(1), ROOMY, ROOMY), new SimpleMeterRegistry(), time);
        tested.acquire("10.0.0.1");
        assertThatThrownBy(() -> tested.acquire("10.0.0.1")).isInstanceOf(RateLimitExceededException.class);

        // when
        time.advance(Duration.ofMinutes(1));

        // then
        assertThatCode(() -> tested.acquire("10.0.0.1")).doesNotThrowAnyException();
    }

    private static OrderCreationRateLimiter limiter(RateLimitProperties.Limit global, RateLimitProperties.Limit perIp,
                                                    RateLimitProperties.Limit perCustomer) {
        return new OrderCreationRateLimiter(properties(global, perIp, perCustomer), new SimpleMeterRegistry(),
                new TestTimeMeter());
    }

    private static RateLimitProperties properties(RateLimitProperties.Limit global, RateLimitProperties.Limit perIp,
                                                  RateLimitProperties.Limit perCustomer) {
        return new RateLimitProperties(global, perIp, perCustomer, 100);
    }

    private static double rejected(MeterRegistry registry, String reason) {
        return registry.get(OrderCreationRateLimiter.REJECTED_METRIC).tag("reason", reason).counter().count();
    }

    // A limit of the given capacity per minute.
    private static RateLimitProperties.Limit limit(long capacity) {
        return new RateLimitProperties.Limit(capacity, Duration.ofMinutes(1));
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
