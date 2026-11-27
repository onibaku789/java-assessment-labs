package study.locker.common;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RpcGuard {
    @ConfigurationProperties("locker.rpc")
    public record Settings(Integer capacity, @DefaultValue("20") int window, @DefaultValue("10") int minimum,
                           @DefaultValue("50") float threshold, @DefaultValue("10s") Duration open,
                           @DefaultValue("2") int probes, @DefaultValue("100ms") Duration backoff,
                           @DefaultValue("true") boolean retry) {
        public Settings {
            if (minimum > window || backoff.isNegative() || !open.isPositive()) {
                throw new IllegalArgumentException("Invalid RPC breaker window or duration");
            }
        }

        private CircuitBreakerConfig breakerConfig() {
            return CircuitBreakerConfig.custom().slidingWindowSize(window).minimumNumberOfCalls(minimum)
                    .failureRateThreshold(threshold).waitDurationInOpenState(open)
                    .permittedNumberOfCallsInHalfOpenState(probes).recordException(RpcFailures::countsAgainstDependency)
                    .ignoreException(RpcFailures::ignoreInBreaker).build();
        }
    }

    private final String dependency;
    private final Duration timeout;
    private final Settings settings;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;
    private final Counter retries;
    private final Counter rejected;

    public RpcGuard(String dependency, Duration timeout, Settings settings, int defaultCapacity,
                    MeterRegistry metrics) {
        if (!timeout.isPositive()) {
            throw new IllegalArgumentException("RPC deadline must be positive");
        }
        this.dependency = dependency;
        this.timeout = timeout;
        this.settings = settings;
        breaker = CircuitBreaker.of(dependency, settings.breakerConfig());
        int permits = settings.capacity() == null ? defaultCapacity : settings.capacity();
        var limits = BulkheadConfig.custom().maxConcurrentCalls(permits).maxWaitDuration(Duration.ZERO).build();
        bulkhead = Bulkhead.of(dependency, limits);

        var tags = Tags.of("dependency", dependency);
        retries = metrics.counter("locker.rpc.retries", tags);
        rejected = metrics.counter("locker.rpc.rejected", tags);
        metrics.gauge("locker.rpc.breaker.state", tags, breaker, RpcGuard::breakerState);
        metrics.gauge("locker.rpc.inflight", tags, bulkhead,
                pool -> permits - pool.getMetrics().getAvailableConcurrentCalls());
    }

    public <T> T call(boolean replayable, Function<Deadline, T> operation) {
        Deadline deadline = callDeadline();
        Supplier<T> attempt = () -> attempt(deadline, operation);
        try {
            // The breaker records one outcome even if the request makes two attempts.
            return breaker.executeSupplier(() -> callWithRetry(attempt, deadline, replayable));
        } catch (CallNotPermittedException refused) {
            throw rejectCall();
        } catch (StatusRuntimeException failure) {
            throw RpcFailures.fromDependency(dependency, failure);
        }
    }

    private Deadline callDeadline() {
        var limit = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        var parent = Context.current().getDeadline();
        return parent == null ? limit : limit.minimum(parent);
    }

    private <T> T callWithRetry(Supplier<T> attempt, Deadline deadline, boolean replayable) {
        boolean allowRetry = replayable && retriesEnabled();
        try {
            return attempt.get();
        } catch (StatusRuntimeException firstFailure) {
            if (!allowRetry || !RpcFailures.canRetry(firstFailure)) {
                throw firstFailure;
            }
            long delay = (long) (settings.backoff().toNanos() * ThreadLocalRandom.current().nextDouble(0.5, 1.5));
            if (deadline.timeRemaining(TimeUnit.NANOSECONDS) <= delay) {
                throw firstFailure;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw Status.CANCELLED.withDescription("RPC interrupted").asRuntimeException();
            }
        }
        retries.increment();
        return attempt.get();
    }

    private boolean retriesEnabled() {
        return settings.retry() && breaker.getState() != CircuitBreaker.State.HALF_OPEN;
    }

    private <T> T attempt(Deadline deadline, Function<Deadline, T> operation) {
        if (deadline.isExpired()) {
            throw Status.DEADLINE_EXCEEDED.asRuntimeException();
        }
        if (Context.current().isCancelled()) {
            throw Status.CANCELLED.asRuntimeException();
        }
        try {
            return bulkhead.executeSupplier(() -> operation.apply(deadline));
        } catch (BulkheadFullException refused) {
            throw rejectCall();
        }
    }

    private StatusRuntimeException rejectCall() {
        rejected.increment();
        return RpcFailures.loadShed(dependency);
    }

    private static double breakerState(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case OPEN, FORCED_OPEN -> 2;
            case HALF_OPEN -> 1;
            default -> 0;
        };
    }
}
