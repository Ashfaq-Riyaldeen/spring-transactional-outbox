package io.github.outbox.notification.e2e;

import org.testcontainers.DockerClientFactory;

public final class Docker {

    private Docker() {
    }

    // On CI the test runs regardless, so a runner that lost Docker fails instead of reporting green.
    public static boolean availableOrCi() {
        return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
    }
}
