package study.parcel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import study.parcel.carrier.grpc.Reply;
import study.parcel.carrier.grpc.Request;
import study.parcel.carrier.grpc.TrackingGrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Week03GrpcCarrierIT {
    private static Server server;
    private static ManagedChannel channel;
    private static GrpcCarrierClient client;

    @BeforeAll
    static void startCarrier() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new TrackingService()).build().start();
        channel = Grpc.newChannelBuilderForAddress("127.0.0.1", server.getPort(), InsecureChannelCredentials.create())
                .build();
        client = new GrpcCarrierClient(channel);
    }

    @Test
    void returnsTheStatusForAKnownParcel() {
        assertThat(client.track("P-100")).contains("IN_TRANSIT");
        assertThat(client.track("P-200")).contains("DELIVERED");
    }

    @Test
    void returnsEmptyForAnUnknownParcel() {
        assertThat(client.track("P-404")).isEmpty();
    }

    @Test
    void preservesUnavailableWhenTheCarrierIsOffline() {
        assertThatThrownBy(() -> client.track("P-OFFLINE"))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aStalledLookupExpiresAndTheNextLookupGetsAFreshDeadline() {
        assertThatThrownBy(() -> client.track("P-STALLED"))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
        assertThat(client.track("P-100")).contains("IN_TRANSIT");
    }

    @AfterAll
    static void stopCarrier() throws InterruptedException {
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static final class TrackingService extends TrackingGrpc.TrackingImplBase {
        @Override
        public void track(Request request, StreamObserver<Reply> response) {
            switch (request.getId()) {
                case "P-100" -> reply(response, "IN_TRANSIT");
                case "P-200" -> reply(response, "DELIVERED");
                case "P-OFFLINE" -> response.onError(Status.UNAVAILABLE.withDescription("Carrier offline").asRuntimeException());
                case "P-STALLED" -> {
                    // Leave the RPC open for the client's deadline. Fail promptly if it has none.
                    if (Context.current().getDeadline() == null) {
                        response.onError(Status.FAILED_PRECONDITION.withDescription("Set a deadline on the lookup").asRuntimeException());
                    }
                }
                default -> response.onError(Status.NOT_FOUND.withDescription("Unknown parcel").asRuntimeException());
            }
        }

        private static void reply(StreamObserver<Reply> response, String status) {
            response.onNext(Reply.newBuilder().setStatus(status).build());
            response.onCompleted();
        }
    }
}
