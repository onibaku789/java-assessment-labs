package study.locker.common;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Downstream failure markers travel through gRPC trailers so each breaker measures its own
 * dependency.
 */
final class RpcFailures {
    private static final Metadata.Key<String> ORIGIN =
            Metadata.Key.of("locker-failure-origin", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> LOAD_SHED =
            Metadata.Key.of("locker-load-shed", Metadata.ASCII_STRING_MARSHALLER);

    private RpcFailures() {}

    static boolean ignoreInBreaker(Throwable failure) {
        return failure instanceof StatusRuntimeException rpc
                && (rpc.getStatus().getCode() == Status.Code.CANCELLED || hasTrailer(rpc, ORIGIN));
    }

    static boolean countsAgainstDependency(Throwable failure) {
        if (!(failure instanceof StatusRuntimeException rpc) || hasTrailer(rpc, ORIGIN)) {
            return false;
        }
        return switch (rpc.getStatus().getCode()) {
            case UNAVAILABLE -> true;
            case DEADLINE_EXCEEDED -> !Context.current().isCancelled();
            default -> false;
        };
    }

    static boolean canRetry(StatusRuntimeException failure) {
        return failure.getStatus().getCode() == Status.Code.UNAVAILABLE
                && !hasTrailer(failure, LOAD_SHED);
    }

    static StatusRuntimeException fromDependency(
            String dependency, StatusRuntimeException failure) {
        if (!countsAgainstDependency(failure)) {
            return failure;
        }
        var trailers = new Metadata();
        if (failure.getTrailers() != null) {
            trailers.merge(failure.getTrailers());
        }
        trailers.put(ORIGIN, dependency);
        return failure.getStatus().asRuntimeException(trailers);
    }

    static StatusRuntimeException loadShed(String dependency) {
        var trailers = new Metadata();
        trailers.put(ORIGIN, dependency);
        trailers.put(LOAD_SHED, "true");
        return Status.UNAVAILABLE
                .withDescription("Dependency temporarily unavailable")
                .asRuntimeException(trailers);
    }

    private static boolean hasTrailer(StatusRuntimeException failure, Metadata.Key<String> key) {
        return failure.getTrailers() != null && failure.getTrailers().containsKey(key);
    }
}
