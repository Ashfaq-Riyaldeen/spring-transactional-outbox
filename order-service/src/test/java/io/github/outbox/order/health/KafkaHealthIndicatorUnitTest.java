package io.github.outbox.order.health;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorUnitTest {

    @Mock
    private Admin admin;

    @Mock
    private DescribeClusterResult cluster;

    @Test
    @DisplayName("UT health() when the cluster answers should be up with its id and number of brokers")
    void health_whenClusterAnswers_shouldBeUpWithItsIdAndNumberOfBrokers() {
        // given
        givenCluster(KafkaFuture.completedFuture("cluster-1"),
                KafkaFuture.completedFuture(List.of(new Node(1, "kafka-1", 9092), new Node(2, "kafka-2", 9092))));
        KafkaHealthIndicator tested = new KafkaHealthIndicator(admin, Duration.ofSeconds(1));

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterId", "cluster-1").containsEntry("nodes", 2);
    }

    @Test
    @DisplayName("UT health() when describing the cluster fails should be down with the error")
    void health_whenDescribingClusterFails_shouldBeDownWithTheError() {
        // given
        KafkaFutureImpl<String> failed = new KafkaFutureImpl<>();
        failed.completeExceptionally(new IllegalStateException("broker unreachable"));
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
        when(cluster.clusterId()).thenReturn(failed);
        KafkaHealthIndicator tested = new KafkaHealthIndicator(admin, Duration.ofSeconds(1));

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("broker unreachable");
    }

    @Test
    @DisplayName("UT health() when the cluster does not answer within the timeout should be down")
    void health_whenClusterDoesNotAnswerWithinTimeout_shouldBeDown() {
        // given
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
        when(cluster.clusterId()).thenReturn(new KafkaFutureImpl<>());
        KafkaHealthIndicator tested = new KafkaHealthIndicator(admin, Duration.ofMillis(50));

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    private void givenCluster(KafkaFuture<String> clusterId, KafkaFuture<Collection<Node>> nodes) {
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
        when(cluster.clusterId()).thenReturn(clusterId);
        when(cluster.nodes()).thenReturn(nodes);
    }
}
