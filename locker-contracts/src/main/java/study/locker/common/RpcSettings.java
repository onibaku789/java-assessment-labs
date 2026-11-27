package study.locker.common;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("locker.rpc")
public record RpcSettings(@DefaultValue("20") int window, @DefaultValue("10") int minimum,
                          @DefaultValue("50") float threshold, @DefaultValue("10s") Duration open,
                          @DefaultValue("2") int probes, @DefaultValue("100ms") Duration backoff,
                          @DefaultValue("true") boolean retry) {
    public RpcSettings {
        if (minimum > window || backoff.isNegative() || !open.isPositive()) {
            throw new IllegalArgumentException("Invalid RPC breaker window or duration");
        }
    }

    CircuitBreakerConfig breakerConfig() {
        return CircuitBreakerConfig.custom().slidingWindowSize(window).minimumNumberOfCalls(minimum)
                .failureRateThreshold(threshold).waitDurationInOpenState(open)
                .permittedNumberOfCallsInHalfOpenState(probes).recordException(RpcFailures::countsAgainstDependency)
                .ignoreException(RpcFailures::ignoreInBreaker).build();
    }
}
