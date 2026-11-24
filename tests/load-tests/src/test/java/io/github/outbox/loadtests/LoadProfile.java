package io.github.outbox.loadtests;

import io.gatling.javaapi.core.OpenInjectionStep;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.github.outbox.loadtests.LoadProperties.doubleProperty;
import static io.github.outbox.loadtests.LoadProperties.intProperty;
import static io.github.outbox.loadtests.LoadProperties.stringProperty;

/**
 * The shape of the load a run puts on the service, picked by the {@code profile} system property. A profile reads
 * its rates and durations from system properties of its own, with the defaults given below.
 */
enum LoadProfile {

    /**
     * {@code rps} (5) from the first second for {@code durationSeconds} (30): the service answers under a light load.
     */
    SMOKE {
        @Override
        List<OpenInjectionStep> injection() {
            return List.of(constantUsersPerSec(doubleProperty("rps", 5))
                    .during(seconds(intProperty("durationSeconds", 30))));
        }
    },

    /**
     * A ramp of {@code rampSeconds} (10) to {@code rps} (50), held for {@code durationSeconds} (300): the latency of a
     * sustained load.
     */
    STEADY {
        @Override
        List<OpenInjectionStep> injection() {
            double rps = doubleProperty("rps", 50);
            return List.of(
                    rampUsersPerSec(0).to(rps).during(seconds(intProperty("rampSeconds", 10))),
                    constantUsersPerSec(rps).during(seconds(intProperty("durationSeconds", 300))));
        }
    },

    /**
     * From {@code startRps} (10) up by {@code stepRps} (10) for {@code steps} (5) steps, each held for
     * {@code stepSeconds} (60) after a ramp of {@code rampSeconds} (10): the rate at which the latency starts to climb.
     */
    PEAK {
        @Override
        List<OpenInjectionStep> injection() {
            double startRps = doubleProperty("startRps", 10);
            double stepRps = doubleProperty("stepRps", 10);
            int steps = intProperty("steps", 5);
            Duration ramp = seconds(intProperty("rampSeconds", 10));
            Duration step = seconds(intProperty("stepSeconds", 60));
            List<OpenInjectionStep> injection = new ArrayList<>();
            double previous = 0;
            for (int i = 0; i < steps; i++) {
                double rate = startRps + i * stepRps;
                injection.add(rampUsersPerSec(previous).to(rate).during(ramp));
                injection.add(constantUsersPerSec(rate).during(step));
                previous = rate;
            }
            return injection;
        }
    };

    abstract List<OpenInjectionStep> injection();

    /**
     * @return the profile named by the {@code profile} system property, {@link #SMOKE} when it is not set
     * @throws IllegalArgumentException when the property names no profile
     */
    static LoadProfile fromSystemProperty() {
        String name = stringProperty("profile", "smoke");
        for (LoadProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown profile '%s', expected one of smoke, steady, peak".formatted(name));
    }

    String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static Duration seconds(int seconds) {
        return Duration.ofSeconds(seconds);
    }
}
