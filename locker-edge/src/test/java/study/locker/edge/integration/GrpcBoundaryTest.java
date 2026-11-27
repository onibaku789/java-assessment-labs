package study.locker.edge.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import study.locker.common.RpcGuard;
import study.locker.edge.adapter.grpc.GrpcLockerOperations;
import study.locker.edge.adapter.security.CurrentCaller;
import study.locker.edge.application.GatewayFailure;


import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class GrpcBoundaryTest {
    private final ReservationProbe upstream = new ReservationProbe();
    private final AtomicInteger tokenReads = new AtomicInteger();
    private GrpcLockerOperations gateway;

    @BeforeEach
    void start() throws Exception {
        var channel = upstream.start();
        var settings = new RpcGuard.Settings(8, 20, 10, 50, Duration.ofSeconds(10), 2, Duration.ofMillis(10), true);
        var guard = new RpcGuard("reservations", Duration.ofMillis(1250), settings, 8, new SimpleMeterRegistry());
        gateway = new GrpcLockerOperations(channel, guard, () -> {
            tokenReads.incrementAndGet();
            return CurrentCaller.tokenValue();
        });
    }

    @AfterEach
    void stop() {
        SecurityContextHolder.clearContext();
        upstream.close();
    }

    @Test
    void forwardsEachCallersTokenOnTheSharedGateway() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> getAs("alice", "first"));
            var second = workers.submit(() -> getAs("bob", "second"));
            assertThat(first.get()).isEqualTo("first");
            assertThat(second.get()).isEqualTo("second");
        }
        assertThat(upstream.calls).containsExactlyInAnyOrder("first Bearer alice", "second Bearer bob");
    }

    @Test
    void reusesTheOriginalTokenWhenRetryingALookup() {
        assertThat(getAs("alice", "retry")).isEqualTo("retry");
        assertThat(upstream.calls).containsExactly("retry Bearer alice", "retry Bearer alice");
        assertThat(tokenReads.get()).isOne();
    }

    @Test
    void missingIdentityFailsBeforeAnOutboundCall() {
        SecurityContextHolder.clearContext();
        assertLookupFails("lookup", "UNAUTHENTICATED");
        assertThat(upstream.calls).isEmpty();
    }

    @Test
    void unauthenticatedJwtFailsBeforeAnOutboundCall() {
        authenticate("alice");
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
        assertLookupFails("lookup", "UNAUTHENTICATED");
        assertThat(upstream.calls).isEmpty();
    }

    @Test
    void propagatesAnUpstreamOutage() {
        authenticate("alice");
        assertLookupFails("offline", "UNAVAILABLE");
    }

    @Test
    void boundsAStalledLookupByTheConfiguredDeadline() {
        authenticate("alice");
        assertLookupFails("stalled", "DEADLINE_EXCEEDED");
        assertThat(upstream.remainingMillis).isBetween(1L, 1250L);
    }

    private void assertLookupFails(String id, String expectedCode) {
        assertThatThrownBy(() -> gateway.get(id)).isInstanceOfSatisfying(GatewayFailure.class,
                ex -> assertThat(ex.code()).isEqualTo(expectedCode));
    }

    private String getAs(String token, String id) {
        authenticate(token);
        try {
            return gateway.get(id).id();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static void authenticate(String token) {
        var jwt = Jwt.withTokenValue(token).header("alg", "RS256").issuer("https://issuer.example.test").subject(token)
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
