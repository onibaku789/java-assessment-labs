package study.locker.edge.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import study.locker.common.RpcGuard;
import study.locker.edge.adapter.security.CallerQuota;
import study.locker.edge.application.*;
import study.locker.edge.bootstrap.EdgeConfig;

import java.time.Duration;

class GatewayPolicyTest {
    private final ReservationProbe upstream = new ReservationProbe();
    private final CallerQuota quota = mock(CallerQuota.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private LockerOperations gateway;

    @BeforeEach
    void start() throws Exception {
        var settings = new RpcGuard.Settings(8, 20, 10, 50, Duration.ofSeconds(10), 2, Duration.ZERO, true);
        gateway = new EdgeConfig().gateway(upstream.start(), metrics, "rest", Duration.ofSeconds(2), settings, quota);
        GrpcBoundaryTest.authenticate("alice");
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        upstream.close();
        metrics.close();
    }

    @Test
    void retryChargesAndRecordsTheLogicalRequestOnce() {
        assertThat(gateway.get("retry").id()).isEqualTo("retry");
        assertThat(upstream.calls).hasSize(2);
        verify(quota).charge("https://issuer.example.test", "alice");
        assertThat(requests("OK")).isOne();
    }

    @Test
    void telemetryIncludesQuotaRejections() {
        doThrow(new GatewayFailure("RESOURCE_EXHAUSTED", "Caller quota exceeded", 2)).when(quota)
                .charge("https://issuer.example.test", "alice");
        assertThatThrownBy(() -> gateway.get("first")).isInstanceOfSatisfying(GatewayFailure.class,
                failure -> assertThat(failure.retryAfterSeconds()).isEqualTo(2));
        assertThat(requests("RESOURCE_EXHAUSTED")).isOne();
        assertThat(upstream.calls).isEmpty();
    }

    private long requests(String outcome) {
        return metrics.get("locker.gateway.requests").tags("operation", "get", "protocol", "rest", "outcome", outcome)
                .timer().count();
    }
}
