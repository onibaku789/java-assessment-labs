package study.locker.edge.adapter.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;

import study.locker.edge.application.*;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class GrpcLockerOperations implements LockerOperations {
    private static Reservation from(Booking b) {
        return new Reservation(b.getId(), b.getCompartmentId(), b.getState());
    }

    private final ReservationsGrpc.ReservationsBlockingStub base;

    private final Duration deadline;

    public GrpcLockerOperations(Channel channel, Duration deadline) {
        if (!deadline.isPositive())
            throw new IllegalArgumentException("Reservations deadline must be positive");
        this.deadline = deadline;
        base = ReservationsGrpc.newBlockingStub(channel);
    }

    private ReservationsGrpc.ReservationsBlockingStub stub(String credential) {
        Metadata metadata = new Metadata();
        metadata.put(RpcIdentity.AUTH, "Bearer " + credential);
        return base.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS);
    }

    public List<Slot> available(String credential, String site, String size) {
        var query = Search.newBuilder().setSite(text(site)).setSize(text(size)).build();
        return rpc(
                () ->
                        stub(credential).available(query).getItemsList().stream()
                                .map(c -> new Slot(c.getId(), c.getSite(), c.getSize()))
                                .toList());
    }

    public Reservation get(String credential, String id) {
        return rpc(() -> from(stub(credential).get(Key.newBuilder().setId(text(id)).build())));
    }

    public Reservation command(
            String credential, Action action, String requestId, String id, String code) {
        var cmd =
                Command.newBuilder()
                        .setId(text(id))
                        .setRequestId(text(requestId))
                        .setCode(text(code))
                        .build();
        var client = stub(credential);
        return rpc(
                () ->
                        from(
                                switch (action) {
                                    case RESERVE -> client.reserve(cmd);
                                    case CANCEL -> client.cancel(cmd);
                                    case DEPOSIT -> client.deposit(cmd);
                                    case REISSUE -> client.reissue(cmd);
                                    case COLLECT -> client.collect(cmd);
                                }));
    }

    private static <T> T rpc(Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException failure) {
            var status = failure.getStatus();
            throw new GatewayFailure(status.getCode().name(), status.getDescription());
        }
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
