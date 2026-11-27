package study.locker.reservation.adapter.grpcserver;

import io.grpc.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import study.locker.reservation.domain.model.ReservationFailure;

@Component
final class ReservationRpcErrors implements GrpcExceptionHandler {
    @Override
    public StatusException handleException(Throwable failure) {
        if (failure instanceof AuthenticationException || failure instanceof AccessDeniedException) return null;
        if (failure instanceof ReservationFailure problem)
            return status(problem).withDescription(problem.getMessage()).asException();
        if (failure instanceof ConstraintViolationException invalid) {
            return Status.INVALID_ARGUMENT.withDescription(
                    invalid.getConstraintViolations().iterator().next().getMessage()).asException();
        }
        if (failure instanceof StatusRuntimeException rpc) return rpc.getStatus().asException(rpc.getTrailers());
        if (failure instanceof StatusException rpc) return rpc;
        System.getLogger(getClass().getName())
                .log(System.Logger.Level.ERROR, "Reservation RPC failed with " + failure.getClass().getName());
        return Status.INTERNAL.withDescription("Service failure").asException();
    }

    private static Status status(ReservationFailure failure) {
        return switch (failure.reason()) {
            case INVALID_INPUT -> Status.INVALID_ARGUMENT;
            case UNKNOWN_RESERVATION -> Status.NOT_FOUND;
            case OCCUPIED, REUSED_KEY -> Status.ALREADY_EXISTS;
            case FORBIDDEN -> Status.PERMISSION_DENIED;
            case INVALID_STATE -> Status.FAILED_PRECONDITION;
        };
    }
}
