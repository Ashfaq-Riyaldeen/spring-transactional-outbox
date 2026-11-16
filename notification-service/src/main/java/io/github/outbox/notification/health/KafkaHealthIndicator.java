package io.github.outbox.notification.health;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Reports whether the Kafka cluster answers: up with the cluster id and the number of brokers, down when describing
 * the cluster fails or does not finish within the timeout.
 */
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final Admin admin;
    private final Duration timeout;

    public KafkaHealthIndicator(Admin admin, Duration timeout) {
        super("Kafka health check failed");
        this.admin = admin;
        this.timeout = timeout;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        DescribeClusterResult cluster = admin.describeCluster(
                new DescribeClusterOptions().timeoutMs((int) timeout.toMillis()));
        String clusterId = cluster.clusterId().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        int nodes = cluster.nodes().get(timeout.toMillis(), TimeUnit.MILLISECONDS).size();
        builder.up()
                .withDetail("clusterId", clusterId)
                .withDetail("nodes", nodes);
    }
}
