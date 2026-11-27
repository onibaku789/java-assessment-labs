package study.locker.edge.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import study.locker.edge.adapter.security.CallerQuota;
import study.locker.edge.application.*;
import study.locker.edge.bootstrap.GatewayOperationsConfiguration;

import java.util.Map;
import java.util.concurrent.*;

class GatewayPolicyTest {
    private final ReservationProbe upstream = new ReservationProbe();
    private final CallerQuota quota = mock(CallerQuota.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private LockerOperations gateway;
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @BeforeEach
    void start() throws Exception {
        var channel = upstream.start();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("locker.rpc.backoff", "0ms", "locker.protocol", "rest", "locker.rpc.capacity", 1)));
        context.registerBean(ManagedChannel.class, () -> channel);
        context.registerBean(CallerQuota.class, () -> quota);
        context.registerBean(SimpleMeterRegistry.class, () -> metrics);
        context.register(GatewayOperationsConfiguration.class);
        context.refresh();
        gateway = context.getBean(LockerOperations.class);
        GrpcBoundaryTest.authenticate("alice");
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        upstream.close();
        context.close();
    }

    @Test
    void retryChargesAndRecordsTheLogicalRequestOnce() {
        assertThat(gateway.get("retry").id()).isEqualTo("retry");
        assertThat(upstream.calls).hasSize(2);
        verify(quota).charge("https://issuer.example.test", "alice");
        assertThat(requests("OK")).isOne();
        assertThat(context.getBean(CircuitBreaker.class).getMetrics().getNumberOfBufferedCalls()).isOne();
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

    @Test
    void aPendingLookupAndAnAvailabilitySearchShareTheSamePermit() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var lookup = threads.submit(() -> {
                GrpcBoundaryTest.authenticate("alice");
                return gateway.get("held");
            });
            try {
                upstream.held.get(3, TimeUnit.SECONDS);
                assertThatThrownBy(() -> gateway.available("", "")).isInstanceOfSatisfying(GatewayFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo("UNAVAILABLE"));
                assertThat(upstream.calls).containsExactly("held Bearer alice");
                assertThat(metrics.get("locker.rpc.rejected").counter().count()).isOne();
            } finally {
                upstream.release.complete(null);
            }
            assertThat(lookup.get().id()).isEqualTo("held");
            assertThat(gateway.available("", "")).isEmpty();
            assertThat(upstream.calls).containsExactly("held Bearer alice", "available");
        }
    }

    private long requests(String outcome) {
        return metrics.get("locker.gateway.requests").tags("operation", "get", "protocol", "rest", "outcome", outcome)
                .timer().count();
    }
}
