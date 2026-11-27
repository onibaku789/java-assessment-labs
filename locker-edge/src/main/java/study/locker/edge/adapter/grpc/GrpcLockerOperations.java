package study.locker.edge.adapter.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;

import study.locker.common.RpcGuard;
import study.locker.edge.application.*;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class GrpcLockerOperations implements LockerOperations {
    private final ReservationsGrpc.ReservationsBlockingStub base;
    private final RpcGuard guard;

    public GrpcLockerOperations(Channel channel, RpcGuard guard) {
        this.guard = guard;
        base = ReservationsGrpc.newBlockingStub(channel);
    }

    private ReservationsGrpc.ReservationsBlockingStub stub(String credential) {
        Metadata metadata = new Metadata();
        metadata.put(RpcIdentity.AUTH, "Bearer " + credential);
        return base.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public List<Slot> available(String credential, String site, String size) {
        var query = Search.newBuilder().setSite(text(site)).setSize(text(size)).build();
        var result = rpc(credential, true, client -> client.available(query));
        return result.getItemsList().stream()
                .map(slot -> new Slot(slot.getId(), slot.getSite(), slot.getSize()))
                .toList();
    }

    public Reservation get(String credential, String id) {
        var key = Key.newBuilder().setId(text(id)).build();
        return reservation(rpc(credential, true, client -> client.get(key)));
    }

    public Reservation command(
            String credential, Action action, String requestId, String id, String code) {
        var cmd =
                Command.newBuilder()
                        .setId(text(id))
                        .setRequestId(text(requestId))
                        .setCode(text(code))
                        .build();
        Booking booking =
                rpc(
                        credential,
                        validKey(requestId),
                        client ->
                                switch (action) {
                                    case RESERVE -> client.reserve(cmd);
                                    case CANCEL -> client.cancel(cmd);
                                    case DEPOSIT -> client.deposit(cmd);
                                    case REISSUE -> client.reissue(cmd);
                                    case COLLECT -> client.collect(cmd);
                                });
        return reservation(booking);
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

    private <T> T rpc(
            String credential,
            boolean replay,
            Function<ReservationsGrpc.ReservationsBlockingStub, T> call) {
        try {
            var client = stub(credential);
            return guard.call(replay, deadline -> call.apply(client.withDeadline(deadline)));
        } catch (StatusRuntimeException failure) {
            var status = failure.getStatus();
            throw new GatewayFailure(status.getCode().name(), status.getDescription());
        }
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
