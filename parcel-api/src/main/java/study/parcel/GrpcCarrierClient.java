package study.parcel;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import study.parcel.carrier.grpc.Reply;
import study.parcel.carrier.grpc.Request;
import study.parcel.carrier.grpc.TrackingGrpc;

final class GrpcCarrierClient {
    private final TrackingGrpc.TrackingBlockingStub carrier;

    GrpcCarrierClient(Channel channel) {
        carrier = TrackingGrpc.newBlockingStub(channel);
    }

    Optional<String> track(String trackingId) {
        var request = Request.newBuilder().setId(trackingId).build();
        try {
            Reply reply = carrier.withDeadlineAfter(1, TimeUnit.SECONDS).track(request);
            return Optional.of(reply.getStatus());
        } catch (StatusRuntimeException failure) {
            if (failure.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw failure;
        }
    }
}
