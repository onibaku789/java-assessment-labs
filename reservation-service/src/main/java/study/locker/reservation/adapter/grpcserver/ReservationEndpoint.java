package study.locker.reservation.adapter.grpcserver;

import org.springframework.grpc.server.service.GrpcService;

import io.grpc.stub.StreamObserver;

import study.locker.reservation.application.ReservationAccess;
import study.locker.reservation.domain.model.Reservation;
import study.locker.wire.*;

import java.util.Base64;

@GrpcService
public final class ReservationEndpoint extends ReservationsGrpc.ReservationsImplBase {
    private final ReservationAccess access;

    public ReservationEndpoint(ReservationAccess access) {
        this.access = access;
    }

    @Override
    public void available(Search q, StreamObserver<Compartments> out) {
        reply(out, Compartments.newBuilder().addAllItems(access.available(q.getSite(), q.getSize()).stream()
                        .map(s -> Compartment.newBuilder().setId(s.id()).setSite(s.site()).setSize(s.size()).build()).toList())
                .build());
    }

    @Override
    public void get(Key q, StreamObserver<Booking> out) {
        reply(out, wire(access.get(q.getId())));
    }

    @Override
    public void reserve(Command q, StreamObserver<Booking> out) {
        reply(out, wire(access.reserve(request("RESERVE", q))));
    }

    @Override
    public void cancel(Command q, StreamObserver<Booking> out) {
        reply(out, wire(access.cancel(request("CANCEL", q))));
    }

    @Override
    public void deposit(Command q, StreamObserver<Booking> out) {
        reply(out, wire(access.deposit(request("DEPOSIT", q))));
    }

    @Override
    public void reissue(Command q, StreamObserver<Booking> out) {
        reply(out, wire(access.reissue(request("REISSUE", q))));
    }

    @Override
    public void collect(Command q, StreamObserver<Booking> out) {
        reply(out, wire(access.collect(request("COLLECT", q), q.getCode())));
    }

    private ReservationAccess.Request request(String operation, Command command) {
        String input = "command:" + operation + ":" + Base64.getEncoder().encodeToString(command.toByteArray());
        return new ReservationAccess.Request(operation, command.getRequestId(), command.getId(), input);
    }

    private static Booking wire(Reservation.View v) {
        return Booking.newBuilder().setId(v.id().toString()).setCompartmentId(v.compartment())
                .setState(v.state().name()).build();
    }

    private static <T> void reply(StreamObserver<T> out, T value) {
        out.onNext(value);
        out.onCompleted();
    }
}
