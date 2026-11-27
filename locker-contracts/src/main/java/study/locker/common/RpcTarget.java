package study.locker.common;

import io.grpc.Context;
import io.grpc.Deadline;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** One downstream dependency, with a shared concurrency budget in the calling process. */
public record RpcTarget(String name, Duration timeout, int capacity) {
    public RpcTarget {
        if (!timeout.isPositive() || capacity < 0) {
            throw new IllegalArgumentException("RPC deadline must be positive and capacity must be nonnegative");
        }
    }

    Deadline deadline() {
        var local = Deadline.after(timeout.toNanos(), TimeUnit.NANOSECONDS);
        var incoming = Context.current().getDeadline();
        return incoming == null ? local : local.minimum(incoming);
    }
}
