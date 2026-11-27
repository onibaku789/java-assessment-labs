package study.locker.edge.adapter.grpc;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import study.locker.common.Rpc;
import study.locker.edge.application.GatewayFailure;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class GrpcBoundaryTest {
    @Test
    void forwardsCallerPropagatesOutageAndBoundsAStalledLookup() throws Exception {
        var caller = new AtomicReference<String>();
        var deadline = new AtomicReference<Deadline>();
        ServerInterceptor capture =
                new ServerInterceptor() {
                    @Override
                    public <Q, R> ServerCall.Listener<Q> interceptCall(
                            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
                        caller.set(headers.get(RpcIdentity.AUTH));
                        deadline.set(Context.current().getDeadline());
                        return next.startCall(call, headers);
                    }
                };
        var server =
                Rpc.serve(
                        0,
                        new ReservationsGrpc.ReservationsImplBase() {
                            @Override
                            public void get(Key request, StreamObserver<Booking> reply) {
                                if (request.getId().equals("offline"))
                                    reply.onError(Status.UNAVAILABLE.asRuntimeException());
                                // "stalled" leaves the RPC open until the client deadline cancels
                                // it.
                            }
                        },
                        capture);
        var channel = Rpc.channel("127.0.0.1:" + server.getPort());
        try {
            var gateway = new GrpcLockerOperations(channel, Duration.ofMillis(1250));
            String actor = "fixture";
            assertThatThrownBy(() -> gateway.get(actor, "offline"))
                    .isInstanceOfSatisfying(
                            GatewayFailure.class,
                            ex -> assertThat(ex.code()).isEqualTo("UNAVAILABLE"));
            assertThat(caller.get()).isEqualTo("Bearer fixture");
            assertThat(deadline.get().timeRemaining(TimeUnit.MILLISECONDS)).isBetween(1L, 1250L);
            assertThatThrownBy(() -> gateway.get(actor, "stalled"))
                    .isInstanceOfSatisfying(
                            GatewayFailure.class,
                            ex -> assertThat(ex.code()).isEqualTo("DEADLINE_EXCEEDED"));
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
