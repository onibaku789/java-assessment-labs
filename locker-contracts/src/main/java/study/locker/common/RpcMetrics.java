package study.locker.common;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.atomic.AtomicInteger;

class RpcMetrics {
    final AtomicInteger inflight = new AtomicInteger();
    final Counter rejected;
    final Counter retries;

    RpcMetrics(MeterRegistry registry, CircuitBreaker breaker) {
        var tags = Tags.of("dependency", breaker.getName());
        retries = registry.counter("locker.rpc.retries", tags);
        rejected = registry.counter("locker.rpc.rejected", tags);
        registry.gauge("locker.rpc.inflight", tags, inflight);
        registry.gauge("locker.rpc.breaker.state", tags, breaker, RpcMetrics::breakerState);
    }

    private static double breakerState(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case OPEN, FORCED_OPEN -> 2;
            case HALF_OPEN -> 1;
            default -> 0;
        };
    }
}
