package study.locker.reservation.adapter.grpcserver;

import io.grpc.*;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import study.locker.common.PickupTransit;
import study.locker.reservation.domain.model.ReservationFailure;

@Component
final class ReservationRpcErrors implements GrpcExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationRpcErrors.class);

    @Override
    public StatusException handleException(Throwable failure) {
        if (failure instanceof PickupTransit.Unavailable)
            return Status.UNAVAILABLE.withDescription("Pickup key service unavailable").asException();
        if (failure instanceof AuthenticationException || failure instanceof AccessDeniedException) return null;
        if (failure instanceof ReservationFailure problem)
            return status(problem).withDescription(problem.getMessage()).asException();
        if (failure instanceof ConstraintViolationException invalid) {
            return Status.INVALID_ARGUMENT.withDescription(
                    invalid.getConstraintViolations().iterator().next().getMessage()).asException();
        }
        return transportFailure(failure);
    }

    private static StatusException transportFailure(Throwable failure) {
        if (failure instanceof StatusRuntimeException rpc) return rpc.getStatus().asException(rpc.getTrailers());
        if (failure instanceof StatusException rpc) return rpc;
        LOG.error("Reservation RPC failed with {}", failure.getClass().getName());
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
