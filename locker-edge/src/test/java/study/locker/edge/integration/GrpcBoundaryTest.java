package study.locker.edge.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import study.locker.common.*;
import study.locker.edge.adapter.grpc.GrpcLockerOperations;
import study.locker.edge.adapter.security.CurrentCaller;
import study.locker.edge.application.GatewayFailure;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class GrpcBoundaryTest {
    private final ReservationProbe upstream = new ReservationProbe();
    private final AtomicInteger tokenReads = new AtomicInteger();
    private GrpcLockerOperations gateway;
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void start() throws Exception {
        var channel = upstream.start();
        context = RpcClientFixture.open(new RpcTarget("reservations", Duration.ofMillis(1250), 8),
                new SimpleMeterRegistry(), Map.of("locker.rpc.backoff", "10ms"));
        gateway = new GrpcLockerOperations(channel, context.getBean(RpcCalls.class), () -> {
            tokenReads.incrementAndGet();
            return CurrentCaller.tokenValue();
        });
    }

    @AfterEach
    void stop() {
        SecurityContextHolder.clearContext();
        upstream.close();
        context.close();
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

    @ParameterizedTest
    @CsvSource({"'', 1", "invalid, 1", "1-1-1-1-1, 1", "4ab43896-722c-4cfc-a177-059fd5b42038, 2"})
    void onlyCommandsWithCanonicalRequestIdsCanBeReplayed(String key, int attempts) {
        authenticate("courier");
        assertThatThrownBy(() -> gateway.deposit(key, "reservation")).isInstanceOfSatisfying(GatewayFailure.class,
                failure -> assertThat(failure.code()).isEqualTo("UNAVAILABLE"));
        assertThat(upstream.calls).containsExactlyElementsOf(Collections.nCopies(attempts, key));
        assertThat(tokenReads.get()).isOne();
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
