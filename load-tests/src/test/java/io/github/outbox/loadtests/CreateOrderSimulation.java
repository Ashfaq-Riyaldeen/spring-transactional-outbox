package io.github.outbox.loadtests;

import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Order creation, {@code POST /api/v1/orders}, at a request rate raised in steps: from {@code startRps} by
 * {@code stepRps} for {@code steps} steps, each held for {@code stepSeconds} after a ramp of {@code rampSeconds}.
 * Every order needs a {@code 201}; the run fails when more than {@code maxFailedPercent} of the requests fail or
 * the 95th percentile of the response time exceeds {@code p95Ms}.
 * <p>
 * All of it is set through system properties, {@code baseUrl} included. Customer ids are taken in turn from a pool
 * of {@code customerPoolSize}, so the per-customer rate limit of the order service is not what the run measures;
 * its global and per-client limits have to be raised for a run above them.
 */
public class CreateOrderSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    private static final double START_RPS = doubleProperty("startRps", 10);
    private static final double STEP_RPS = doubleProperty("stepRps", 10);
    private static final int STEPS = intProperty("steps", 5);
    private static final int STEP_SECONDS = intProperty("stepSeconds", 60);
    private static final int RAMP_SECONDS = intProperty("rampSeconds", 10);

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

    private final ScenarioBuilder createOrder = scenario("Create order")
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
        setUp(createOrder.injectOpen(steppedRate()))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lte(MAX_FAILED_PERCENT),
                        global().responseTime().percentile(95).lte(P95_MS));
    }

    private static List<OpenInjectionStep> steppedRate() {
        List<OpenInjectionStep> injection = new ArrayList<>();
        double previous = 0;
        for (int step = 0; step < STEPS; step++) {
            double rate = START_RPS + step * STEP_RPS;
            injection.add(rampUsersPerSec(previous).to(rate).during(Duration.ofSeconds(RAMP_SECONDS)));
            injection.add(constantUsersPerSec(rate).during(Duration.ofSeconds(STEP_SECONDS)));
            previous = rate;
        }
        return injection;
    }

    private static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    private static double doubleProperty(String name, double defaultValue) {
        return Double.parseDouble(System.getProperty(name, String.valueOf(defaultValue)));
    }
}
