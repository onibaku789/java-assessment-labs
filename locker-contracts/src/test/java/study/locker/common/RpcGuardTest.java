package study.locker.common;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.grpc.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class RpcGuardTest {
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private RpcGuard guard(String target, int capacity) {
        return new RpcGuard(
                target,
                Duration.ofMillis(250),
                new RpcGuard.Settings(
                        capacity, 2, 2, 50, Duration.ofMillis(500), 1, Duration.ZERO, true),
                capacity,
                metrics);
    }

    @Test
    void directoryOutageShedsSearchesAndLeavesIndependentLookupsAvailable() {
        var directory = guard("directory", 1);
        var reservations = guard("reservations", 4);
        var directoryAttempts = new AtomicInteger();
        Function<Deadline, String> unavailableDirectory =
                deadline -> {
                    directoryAttempts.incrementAndGet();
                    throw Status.UNAVAILABLE.asRuntimeException();
                };
        Function<Deadline, String> search = deadline -> directory.call(false, unavailableDirectory);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> reservations.call(true, search))
                    .isInstanceOf(StatusRuntimeException.class);
        }
        assertThat(directoryAttempts.get()).isEqualTo(2);
        assertThat(reservations.<String>call(false, deadline -> "healthy")).isEqualTo("healthy");
        await().ignoreExceptionsMatching(
                        ex ->
                                ex instanceof StatusRuntimeException rpc
                                        && rpc.getStatus().getCode() == Status.Code.UNAVAILABLE)
                .untilAsserted(
                        () ->
                                assertThat(directory.<String>call(false, deadline -> "probe"))
                                        .isEqualTo("probe"));
        assertThat(
                        metrics.get("locker.rpc.breaker.state")
                                .tag("dependency", "directory")
                                .gauge()
                                .value())
                .isZero();
    }

    @Test
    void fullBulkheadRejectsImmediatelyAndReturnsPermit() throws Exception {
        var guard = guard("reservations", 1);
        var started = new CountDownLatch(1);
        var release = new CompletableFuture<String>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    threads.submit(
                            () ->
                                    guard.call(
                                            false,
                                            deadline -> {
                                                started.countDown();
                                                return release.join();
                                            }));
            try {
                assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> guard.call(true, deadline -> "second"))
                        .hasMessageContaining("UNAVAILABLE");
                assertThat(metrics.get("locker.rpc.retries").counter().count()).isZero();
            } finally {
                release.complete("done");
            }
            assertThat(first.get()).isEqualTo("done");
            assertThat(guard.<String>call(false, deadline -> "healthy")).isEqualTo("healthy");
        }
    }

    @Test
    void aRetrySharesTheOriginalDeadlineAndBusinessErrorsDoNotRetry() {
        var guard = guard("reservations", 2);
        var deadlines = new ArrayList<Deadline>();
        var result =
                guard.call(
                        true,
                        deadline -> {
                            deadlines.add(deadline);
                            if (deadlines.size() == 1)
                                throw Status.UNAVAILABLE.asRuntimeException();
                            return "ok";
                        });
        assertThat(result).isEqualTo("ok");
        assertThat(deadlines).hasSize(2);
        assertThat(deadlines.getLast()).isSameAs(deadlines.getFirst());
        var healthy = guard("healthy", 2);
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(
                            () ->
                                    healthy.call(
                                            true,
                                            d -> {
                                                throw Status.NOT_FOUND.asRuntimeException();
                                            }))
                    .hasMessageContaining("NOT_FOUND");
        assertThat(
                        metrics.get("locker.rpc.retries")
                                .tag("dependency", "reservations")
                                .counter()
                                .count())
                .isEqualTo(1);
    }
}
