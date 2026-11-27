package study.locker.common;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.resilience.InvocationRejectedException;

import java.util.function.Function;

/** The breaker observes the final result. Each wire attempt separately acquires capacity. */
public class RpcCalls {
    private static final String TARGET = "@rpcTarget.name()";
    private final RpcTarget target;
    private final RpcAttempt attempts;
    private final RpcMetrics metrics;
    private final RpcRetry retry;

    RpcCalls(RpcTarget target, RpcAttempt attempts, RpcMetrics metrics, RpcRetry retry) {
        this.target = target;
        this.attempts = attempts;
        this.metrics = metrics;
        this.retry = retry;
    }

    @CircuitBreaker(name = TARGET, fallbackMethod = "rpcFailure")
    public <T> T once(Function<Deadline, T> operation) {
        return attempt(target.deadline(), operation);
    }

    @CircuitBreaker(name = TARGET, fallbackMethod = "rpcFailure")
    public <T> T replayable(Function<Deadline, T> operation) {
        var deadline = target.deadline();
        return retry.execute(deadline, () -> attempt(deadline, operation));
    }

    private <T> T attempt(Deadline deadline, Function<Deadline, T> operation) {
        if (deadline.isExpired()) {
            throw Status.DEADLINE_EXCEEDED.asRuntimeException();
        }
        if (Context.current().isCancelled()) {
            throw Status.CANCELLED.asRuntimeException();
        }
        return attempts.invoke(() -> operation.apply(deadline));
    }

    private Object rpcFailure(StatusRuntimeException failure) {
        throw RpcFailures.fromDependency(target.name(), failure);
    }

    private Object rpcFailure(CallNotPermittedException failure) {
        throw rejected();
    }

    private Object rpcFailure(InvocationRejectedException failure) {
        throw rejected();
    }

    private StatusRuntimeException rejected() {
        metrics.rejected.increment();
        return RpcFailures.loadShed(target.name());
    }
}
