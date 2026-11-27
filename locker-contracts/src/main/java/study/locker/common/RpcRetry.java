package study.locker.common;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.core.retry.*;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

record RpcRetry(RpcSettings settings, CircuitBreaker breaker, RpcMetrics metrics) implements RetryListener {

    <T> T execute(Deadline deadline, Supplier<T> attempt) {
        if (!settings.retry() || breaker.getState() == CircuitBreaker.State.HALF_OPEN) {
            return attempt.get();
        }
        return before(deadline, settings.backoff()).invoke(attempt);
    }

    private RetryTemplate before(Deadline deadline, Duration backoff) {
        long delay = (long) (backoff.toNanos() * ThreadLocalRandom.current().nextDouble(0.5, 1.5));
        var policy = RetryPolicy.builder().maxRetries(1).delay(Duration.ofNanos(delay))
                .predicate(failure -> failure instanceof StatusRuntimeException rpc && RpcFailures.canRetry(rpc)
                        && !Context.current().isCancelled() && deadline.timeRemaining(TimeUnit.NANOSECONDS) > delay)
                .build();
        var template = new RetryTemplate(policy);
        template.setRetryListener(this);
        return template;
    }

    @Override
    public void beforeRetry(RetryPolicy policy, Retryable<?> operation, RetryState state) {
        metrics.retries.increment();
    }

    @Override
    public void onRetryPolicyInterruption(RetryPolicy policy, Retryable<?> operation, RetryException failure) {
        throw Status.CANCELLED.withDescription("RPC interrupted").asRuntimeException();
    }
}
