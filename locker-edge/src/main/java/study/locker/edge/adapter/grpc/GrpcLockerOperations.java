package study.locker.edge.adapter.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;

import study.locker.common.RpcCalls;
import study.locker.edge.application.*;
import study.locker.common.Rpc;
import study.locker.wire.*;
import study.locker.wire.ReservationsGrpc.ReservationsBlockingStub;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class GrpcLockerOperations implements LockerOperations {
    private final ReservationsBlockingStub base;
    private final Supplier<String> callerToken;
    private final RpcCalls calls;

    public GrpcLockerOperations(Channel channel, RpcCalls calls, Supplier<String> callerToken) {
        this.calls = calls;
        this.callerToken = callerToken;
        base = ReservationsGrpc.newBlockingStub(channel);
    }

    private ReservationsBlockingStub authenticatedStub() {
        Metadata metadata = new Metadata();
        metadata.put(Rpc.AUTHORIZATION, "Bearer " + callerToken.get());
        return base.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Override
    public List<Slot> available(String site, String size) {
        var query = Search.newBuilder().setSite(text(site)).setSize(text(size)).build();
        var result = rpc(true, client -> client.available(query));
        return result.getItemsList().stream().map(slot -> new Slot(slot.getId(), slot.getSite(), slot.getSize()))
                .toList();
    }

    @Override
    public Reservation get(String reservationId) {
        var key = Key.newBuilder().setId(text(reservationId)).build();
        return reservation(rpc(true, client -> client.get(key)));
    }

    @Override
    public Reservation reserve(String requestId, String compartmentId) {
        return send(commandFor(requestId, compartmentId), ReservationsBlockingStub::reserve);
    }

    @Override
    public Reservation cancel(String requestId, String reservationId) {
        return send(commandFor(requestId, reservationId), ReservationsBlockingStub::cancel);
    }

    @Override
    public Reservation deposit(String requestId, String reservationId) {
        return send(commandFor(requestId, reservationId), ReservationsBlockingStub::deposit);
    }

    @Override
    public Reservation reissue(String requestId, String reservationId) {
        return send(commandFor(requestId, reservationId), ReservationsBlockingStub::reissue);
    }

    @Override
    public Reservation collect(String requestId, String reservationId, String code) {
        return send(commandFor(requestId, reservationId).setCode(text(code)), ReservationsBlockingStub::collect);
    }

    private static Command.Builder commandFor(String requestId, String targetId) {
        return Command.newBuilder().setRequestId(text(requestId)).setId(text(targetId));
    }

    private Reservation send(Command.Builder builder,
                             BiFunction<ReservationsBlockingStub, Command, Booking> operation) {
        var command = builder.build();
        return reservation(rpc(validKey(command.getRequestId()), client -> operation.apply(client, command)));
    }

    private static Reservation reservation(Booking booking) {
        return new Reservation(booking.getId(), booking.getCompartmentId(), booking.getState());
    }

    private static boolean validKey(String key) {
        if (key == null) return false;
        try {
            return UUID.fromString(key).toString().equalsIgnoreCase(key);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private <T> T rpc(boolean replay, Function<ReservationsBlockingStub, T> call) {
        try {
            var client = authenticatedStub();
            Function<Deadline, T> attempt = deadline -> call.apply(client.withDeadline(deadline));
            return replay ? calls.replayable(attempt) : calls.once(attempt);
        } catch (StatusRuntimeException failure) {
            var status = failure.getStatus();
            throw new GatewayFailure(status.getCode().name(), status.getDescription());
        }
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
