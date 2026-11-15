package io.github.outbox.order.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

/**
 * Lets an order creation through while the rate limits allow it, a token bucket per limit and per key. A key idle
 * for a whole period would have a full bucket again, so it is forgotten after that long. Every refusal is counted in
 * {@value #REJECTED_METRIC} under the {@code reason} of the limit that refused it.
 */
public class OrderCreationRateLimiter {

    public static final String REJECTED_METRIC = "orders.creation.rejected";

    private final RateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Bucket global;
    private final Cache<String, Bucket> perIp;
    private final Cache<Long, Bucket> perCustomer;
    private final Counter globalRejections;
    private final Counter perIpRejections;
    private final Counter perCustomerRejections;

    public OrderCreationRateLimiter(RateLimitProperties properties, MeterRegistry meterRegistry) {
        this(properties, meterRegistry, TimeMeter.SYSTEM_MILLISECONDS);
    }

    OrderCreationRateLimiter(RateLimitProperties properties, MeterRegistry meterRegistry, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
        this.global = bucket(properties.global());
        this.perIp = buckets(properties.perIp(), properties.maxTrackedKeys());
        this.perCustomer = buckets(properties.perCustomer(), properties.maxTrackedKeys());
        this.globalRejections = rejections(meterRegistry, "global");
        this.perIpRejections = rejections(meterRegistry, "per_ip");
        this.perCustomerRejections = rejections(meterRegistry, "per_customer");
    }

    /**
     * The counter of the requests refused for one reason; registered up front, so that a reason nothing was refused
     * for yet is reported as zero rather than missing.
     */
    static Counter rejections(MeterRegistry meterRegistry, String reason) {
        return Counter.builder(REJECTED_METRIC)
                .description("Order creation requests refused before reaching the service")
                .tag("reason", reason)
                .register(meterRegistry);
    }

    /**
     * Takes one request from the limit of the client's address and then from the global limit. The address is
     * asked first, so that one busy address used up does not take from what every other caller shares.
     *
     * @param clientIp address the request came from
     * @throws RateLimitExceededException when one of the limits is used up; it carries the wait until the next
     *                                    request fits
     */
    public void acquire(String clientIp) {
        consume(perIp.get(clientIp, key -> bucket(properties.perIp())), perIpRejections,
                "Too many orders from this address");
        consume(global, globalRejections, "Too many orders are being created");
    }

    /**
     * Takes one order from the limit of the customer.
     *
     * @param customerId customer placing the order
     * @throws RateLimitExceededException when the customer's limit is used up
     */
    public void acquireForCustomer(long customerId) {
        consume(perCustomer.get(customerId, key -> bucket(properties.perCustomer())), perCustomerRejections,
                "Too many orders for customer " + customerId);
    }

    private static void consume(Bucket bucket, Counter rejections, String reason) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            rejections.increment();
            throw new RateLimitExceededException(reason + ", retry later", retryAfter(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket bucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private static <K> Cache<K, Bucket> buckets(RateLimitProperties.Limit limit, long maxTrackedKeys) {
        return Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterAccess(limit.period())
                .build();
    }

    // Retry-After is in whole seconds; a wait shorter than a second is rounded up rather than sent as zero.
    private static Duration retryAfter(long nanos) {
        long seconds = Math.max(1, Duration.ofNanos(nanos).plusNanos(999_999_999).toSeconds());
        return Duration.ofSeconds(seconds);
    }
}
