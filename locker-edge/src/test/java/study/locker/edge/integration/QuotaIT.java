package study.locker.edge.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

import study.locker.edge.adapter.security.CallerQuota;
import study.locker.edge.application.GatewayFailure;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Testcontainers
class QuotaIT {
    @Container
    static final GenericContainer<?> valkey =
            new GenericContainer<>("valkey/valkey:8.1.12-alpine").withExposedPorts(6379);

    @Test
    void replicasShareTokensAndRecoverFromABoundedLocalFallback() {
        var settings =
                new CallerQuota.Settings(
                        "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379),
                        Duration.ofMillis(100),
                        1,
                        2,
                        1,
                        1,
                        100);
        var events = new CopyOnWriteArrayList<String>();
        try (var rest = new CallerQuota(settings, events::add);
                var soap = new CallerQuota(settings, events::add)) {
            await().untilAsserted(
                            () -> {
                                events.clear();
                                String probe = UUID.randomUUID().toString();
                                rest.charge("issuer", probe);
                                soap.charge("issuer", probe);
                                assertThat(events).isEmpty();
                            });
            rest.charge("issuer", "alice");
            soap.charge("issuer", "alice");
            assertThatThrownBy(() -> rest.charge("issuer", "alice"))
                    .isInstanceOfSatisfying(
                            GatewayFailure.class,
                            ex -> assertThat(ex.retryAfterSeconds()).isPositive());
            rest.charge("issuer", "bob");
            var docker = valkey.getDockerClient();
            docker.pauseContainerCmd(valkey.getContainerId()).exec();
            try {
                rest.charge("issuer", "offline");
                assertThatThrownBy(() -> rest.charge("issuer", "offline"))
                        .hasMessageContaining("quota");
                soap.charge("issuer", "offline");
                assertThat(events).contains("fallback");
            } finally {
                docker.unpauseContainerCmd(valkey.getContainerId()).exec();
            }
            await().untilAsserted(
                            () -> {
                                events.clear();
                                assertThatThrownBy(() -> soap.charge("issuer", "alice"))
                                        .hasMessageContaining("quota");
                                assertThat(events).containsExactly("rejected");
                            });
        }
    }
}
