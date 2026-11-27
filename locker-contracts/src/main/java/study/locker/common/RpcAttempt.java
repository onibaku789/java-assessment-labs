package study.locker.common;

import org.springframework.resilience.annotation.ConcurrencyLimit;

import java.util.function.Supplier;

import static org.springframework.resilience.annotation.ConcurrencyLimit.ThrottlePolicy.REJECT;

@ConcurrencyLimit(limitString = "#{@rpcTarget.capacity()}", policy = REJECT)
class RpcAttempt {
    private final RpcMetrics metrics;

    RpcAttempt(RpcMetrics metrics) {
        this.metrics = metrics;
    }

    public <T> T invoke(Supplier<T> operation) {
        metrics.inflight.incrementAndGet();
        try {
            return operation.get();
        } finally {
            metrics.inflight.decrementAndGet();
        }
    }
}
