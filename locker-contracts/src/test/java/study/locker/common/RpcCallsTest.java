package study.locker.common;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class RpcCallsTest {
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();

    private RpcCalls client(String target, int capacity) {
        return context(target, capacity, "0ms").getBean(RpcCalls.class);
    }

    private AnnotationConfigApplicationContext context(String target, int capacity, String backoff) {
        var context = RpcClientFixture.open(new RpcTarget(target, Duration.ofSeconds(10), capacity), metrics,
                Map.of("locker.rpc.minimum", 2, "locker.rpc.window", 2, "locker.rpc.open", "500ms",
                        "locker.rpc.probes", 1, "locker.rpc.backoff", backoff));
        contexts.add(context);
        return context;
    }

    @AfterEach
    void closeClients() {
        contexts.forEach(AnnotationConfigApplicationContext::close);
        metrics.close();
    }

    @Test
    void directoryOutageShedsSearchesAndLeavesIndependentLookupsAvailable() {
        var directory = client("directory", 1);
        var reservations = client("reservations", 4);
        var directoryAttempts = new AtomicInteger();
        Function<Deadline, String> unavailableDirectory = deadline -> {
            directoryAttempts.incrementAndGet();
            throw Status.UNAVAILABLE.asRuntimeException();
        };
        Function<Deadline, String> search = deadline -> directory.once(unavailableDirectory);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> reservations.replayable(search))
                    .isInstanceOf(StatusRuntimeException.class);
        }
        assertThat(directoryAttempts.get()).isEqualTo(2);
        assertThat(reservations.<String>once(deadline -> "healthy")).isEqualTo("healthy");
        await().ignoreExceptionsMatching(
                        ex ->
                                ex instanceof StatusRuntimeException rpc
                                        && rpc.getStatus().getCode() == Status.Code.UNAVAILABLE)
                .untilAsserted(
                        () ->
                                assertThat(directory.<String>once(deadline -> "probe"))
                                        .isEqualTo("probe"));
        assertThat(
                        metrics.get("locker.rpc.breaker.state")
                                .tag("dependency", "directory")
                                .gauge()
                                .value())
                .isZero();
    }

    @Test
    void zeroCapacityRejectsBeforeDispatchAndAnAlreadyCancelledCallerGetsCancelled() throws Exception {
        var calls = client("reservations", 0);
        Function<Deadline, String> unexpected = deadline -> {
            throw new AssertionError("Unexpected outbound call");
        };
        assertThatThrownBy(() -> calls.replayable(unexpected)).hasMessageContaining("UNAVAILABLE");
        try (var request = Context.current().withCancellation()) {
            request.cancel(null);
            assertThatThrownBy(() -> request.call(() -> calls.once(unexpected))).hasMessageContaining("CANCELLED");
        }
        assertThat(metrics.get("locker.rpc.rejected").counter().count()).isOne();
        assertThat(metrics.get("locker.rpc.retries").counter().count()).isZero();
    }

    @Test
    void fullBulkheadRejectsImmediatelyAndReturnsPermit() throws Exception {
        var calls = client("reservations", 1);
        var started = new CountDownLatch(1);
        var release = new CompletableFuture<String>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> calls.once(deadline -> {
                started.countDown();
                return release.join();
            }));
            try {
                assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> calls.replayable(deadline -> "second"))
                        .hasMessageContaining("UNAVAILABLE");
                assertThat(metrics.get("locker.rpc.retries").counter().count()).isZero();
            } finally {
                release.complete("done");
            }
            assertThat(first.get()).isEqualTo("done");
            assertThat(calls.<String>once(deadline -> "healthy")).isEqualTo("healthy");
        }
    }

    @Test
    void aShortIncomingDeadlineSkipsBackoffAndPreservesTheOriginalFailure() throws Exception {
        var calls = context("reservations", 1, "20s").getBean(RpcCalls.class);
        var incoming = Deadline.after(2, TimeUnit.SECONDS);
        try (var scheduler = Executors.newSingleThreadScheduledExecutor();
             var request = Context.current().withDeadline(incoming, scheduler)) {
            var attempts = new AtomicInteger();
            assertThatThrownBy(() -> request.call(() -> calls.replayable(deadline -> {
                attempts.incrementAndGet();
                assertThat(deadline).isSameAs(incoming);
                throw Status.UNAVAILABLE.withDescription("first failure").asRuntimeException();
            }))).isInstanceOf(StatusRuntimeException.class).hasMessageContaining("first failure");
            assertThat(attempts.get()).isOne();
            assertThat(metrics.get("locker.rpc.retries").counter().count()).isZero();
        }
    }

    @Test
    void aFailedHalfOpenProbeMakesOneWireAttempt() {
        var context = context("reservations", 1, "0ms");
        var breaker = context.getBean(CircuitBreaker.class);
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> context.getBean(RpcCalls.class).replayable(deadline -> {
            attempts.incrementAndGet();
            throw Status.UNAVAILABLE.asRuntimeException();
        })).isInstanceOf(StatusRuntimeException.class);
        assertThat(attempts.get()).isOne();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void anotherCallCanUseThePermitDuringRetryBackoff() throws Exception {
        var calls = context("reservations", 1, "1s").getBean(RpcCalls.class);
        var attempts = new AtomicInteger();
        var firstFailure = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var waiting = threads.submit(() -> calls.replayable(deadline -> {
                if (attempts.incrementAndGet() == 1) {
                    firstFailure.countDown();
                    throw Status.UNAVAILABLE.asRuntimeException();
                }
                return "recovered";
            }));
            assertThat(firstFailure.await(3, TimeUnit.SECONDS)).isTrue();
            await().pollDelay(Duration.ZERO).until(() -> metrics.get("locker.rpc.inflight").gauge().value() == 0);
            assertThat(calls.<String>once(deadline -> "other call")).isEqualTo("other call");
            assertThat(waiting.isDone()).isFalse();
            assertThat(waiting.get(4, TimeUnit.SECONDS)).isEqualTo("recovered");
        }
    }

    @Test
    void aRetrySharesTheOriginalDeadlineAndBusinessErrorsDoNotRetry() {
        var calls = client("reservations", 2);
        var deadlines = new ArrayList<Deadline>();
        var result = calls.replayable(deadline -> {
            deadlines.add(deadline);
            if (deadlines.size() == 1) {
                throw Status.UNAVAILABLE.asRuntimeException();
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(deadlines).hasSize(2);
        assertThat(deadlines.getLast()).isSameAs(deadlines.getFirst());
        var healthy = client("healthy", 2);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> healthy.replayable(deadline -> {
                throw Status.NOT_FOUND.asRuntimeException();
            })).hasMessageContaining("NOT_FOUND");
        }
        assertThat(metrics.get("locker.rpc.retries").tag("dependency", "healthy").counter().count()).isZero();
        assertThat(metrics.get("locker.rpc.retries").tag("dependency", "reservations").counter().count()).isOne();
    }
}
