package study.locker.reservation.adapter.grpcserver;

import static study.locker.reservation.application.ReservationCommands.Action;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import jakarta.validation.ConstraintViolationException;

import study.locker.common.*;
import study.locker.reservation.application.*;
import study.locker.reservation.domain.model.*;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.util.*;
import java.util.function.Supplier;

public final class ReservationEndpoint extends ReservationsGrpc.ReservationsImplBase {
    private final ReservationAccess access;
    private final PickupSecrets secrets;

    public ReservationEndpoint(ReservationAccess access, PickupSecrets secrets) {
        this.access = access;
        this.secrets = secrets;
    }

    public void available(Search q, StreamObserver<Compartments> out) {
        reply(
                out,
                () ->
                        Compartments.newBuilder()
                                .addAllItems(
                                        access.available(q.getSite(), q.getSize()).stream()
                                                .map(
                                                        s ->
                                                                Compartment.newBuilder()
                                                                        .setId(s.id())
                                                                        .setSite(s.site())
                                                                        .setSize(s.size())
                                                                        .build())
                                                .toList())
                                .build());
    }

    public void get(Key q, StreamObserver<Booking> out) {
        reply(out, () -> wire(access.get(q.getId())));
    }

    public void reserve(Command q, StreamObserver<Booking> out) {
        command(Action.RESERVE, q, out);
    }

    public void cancel(Command q, StreamObserver<Booking> out) {
        command(Action.CANCEL, q, out);
    }

    public void deposit(Command q, StreamObserver<Booking> out) {
        command(Action.DEPOSIT, q, out);
    }

    public void reissue(Command q, StreamObserver<Booking> out) {
        command(Action.REISSUE, q, out);
    }

    public void collect(Command q, StreamObserver<Booking> out) {
        command(Action.COLLECT, q, out);
    }

    private void command(Action action, Command q, StreamObserver<Booking> out) {
        reply(
                out,
                () -> {
                    String fingerprint =
                            secrets.fingerprint(
                                    "command:"
                                            + action
                                            + ":"
                                            + Base64.getEncoder().encodeToString(q.toByteArray()));
                    var cmd =
                            new ReservationAccess.Request(
                                    q.getRequestId(), q.getId(), q.getCode(), fingerprint);
                    return wire(access.execute(action, cmd));
                });
    }

    private static Booking wire(Reservation.View v) {
        return Booking.newBuilder()
                .setId(v.id().toString())
                .setCompartmentId(v.compartment())
                .setState(v.state().name())
                .build();
    }

    private static <T> void reply(StreamObserver<T> out, Supplier<T> work) {
        Rpc.reply(
                out,
                () -> {
                    try {
                        return RpcIdentity.asAuthenticated(work);
                    } catch (ConstraintViolationException ex) {
                        throw Status.INVALID_ARGUMENT
                                .withDescription(
                                        ex.getConstraintViolations().iterator().next().getMessage())
                                .asRuntimeException();
                    } catch (ReservationFailure ex) {
                        var status =
                                switch (ex.reason()) {
                                    case INVALID_INPUT -> Status.INVALID_ARGUMENT;
                                    case UNKNOWN_RESERVATION -> Status.NOT_FOUND;
                                    case OCCUPIED, REUSED_KEY -> Status.ALREADY_EXISTS;
                                    case FORBIDDEN -> Status.PERMISSION_DENIED;
                                    case INVALID_STATE -> Status.FAILED_PRECONDITION;
                                };
                        throw status.withDescription(ex.getMessage()).asRuntimeException();
                    }
                });
    }
}
