package io.github.outbox.loadtests;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.github.outbox.loadtests.LoadProperties.doubleProperty;
import static io.github.outbox.loadtests.LoadProperties.intProperty;
import static io.github.outbox.loadtests.LoadProperties.stringProperty;

/**
 * Order creation, {@code POST /api/v1/orders}, under the {@link LoadProfile} named by the {@code profile} system
 * property. Every order needs a {@code 201}; the run fails when more than {@code maxFailedPercent} of the requests
 * fail or the 95th percentile of the response time exceeds {@code p95Ms}.
 * <p>
 * All of it is set through system properties, {@code baseUrl} included. Customer ids are taken in turn from a pool
 * of {@code customerPoolSize}, so the per-customer rate limit of the order service is not what the run measures;
 * its global and per-client limits have to be raised for a run above them.
 */
public class CreateOrderSimulation extends Simulation {

    private static final String BASE_URL = stringProperty("baseUrl", "http://localhost:8080");
    private static final LoadProfile PROFILE = LoadProfile.fromSystemProperty();

    private static final double MAX_FAILED_PERCENT = doubleProperty("maxFailedPercent", 1.0);
    private static final int P95_MS = intProperty("p95Ms", 500);

    private final CustomerIdPool customerIds = new CustomerIdPool(intProperty("customerPoolSize", 1000));

    private final Iterator<Map<String, Object>> orders = Stream.generate(() -> Map.<String, Object>of(
                    "customerId", customerIds.next(),
                    "correlationId", "load-" + UUID.randomUUID()))
            .iterator();

    private final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    private final ScenarioBuilder createOrder = scenario("Create order (%s)".formatted(PROFILE.displayName()))
            .feed(orders)
            .exec(http("POST /api/v1/orders")
                    .post("/api/v1/orders")
                    .header("X-Correlation-Id", "#{correlationId}")
                    .body(StringBody("""
                            {
                              "customerId": #{customerId},
                              "items": [
                                {"productId": "sku-1", "quantity": 2, "price": 10.50},
                                {"productId": "sku-2", "quantity": 1, "price": 5.25}
                              ]
                            }
                            """))
                    .check(status().is(201)));

    public CreateOrderSimulation() {
        setUp(createOrder.injectOpen(PROFILE.injection()))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lte(MAX_FAILED_PERCENT),
                        global().responseTime().percentile(95).lte(P95_MS));
    }
}
