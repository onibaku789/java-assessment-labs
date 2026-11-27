package study.locker.reservation.integration;

import static io.grpc.Status.Code.FAILED_PRECONDITION;
import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.reservation.integration.GrpcFailureAssert.assertThatGrpcFailure;

import io.grpc.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import study.locker.common.*;
import study.locker.wire.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

class LostCommandReplyIT extends ReservationFixture {

    private static final class DropFirstReplyPerMethod implements ServerInterceptor {
        private final Set<String> seen = ConcurrentHashMap.newKeySet();

        @Override
        public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                           ServerCallHandler<Q, R> next) {
            if (!seen.add(call.getMethodDescriptor().getBareMethodName())) {
                return next.startCall(call, headers);
            }

            var lostReply = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void sendMessage(R message) {
                    // Let the service commit the command, then discard its reply.
                }

                @Override
                public void close(Status actual, Metadata trailers) {
                    super.close(Status.UNAVAILABLE.withDescription("Lost response"), new Metadata());
                }
            };
            return next.startCall(lostReply, headers);
        }
    }

    @Test
    void lostCommandResponsesReplayTheCommittedReservationAndRejectedPickup() throws Exception {
        var faulty = ServerBuilder.forPort(0).addService(server.reservations()).intercept(new DropFirstReplyPerMethod())
                .build().start();
        var link = Rpc.channel("127.0.0.1:" + faulty.getPort());
        var metrics = new SimpleMeterRegistry();
        var target = new RpcTarget("reservations", Duration.ofSeconds(2), 4);
        try (var context = RpcClientFixture.open(target, metrics, Map.of("locker.rpc.backoff", "0ms"))) {
            var calls = context.getBean(RpcCalls.class);
            var gateway = client(link, customer.getSubject());
            var reserve = cmd("CENTRAL-S1");
            Booking booking = calls.replayable(limit -> gateway.withDeadline(limit).reserve(reserve));
            assertRowCounts(1, 1, 1);
            deposit(booking.getId());
            var reject = cmd(booking.getId()).toBuilder().setCode("wrong").build();
            assertThatGrpcFailure(() -> calls.replayable(limit -> gateway.withDeadline(limit).collect(reject)))
                    .hasCode(FAILED_PRECONDITION)
                    .hasDescriptionContaining("Invalid pickup code");
            assertThat(db.sql("select attempts from booking").query(Integer.class).single()).isOne();
            assertThat(db.sql("select count(*) from command_result").query(Integer.class).single()).isEqualTo(3);
            assertThat(metrics.get("locker.rpc.retries").counter().count()).isEqualTo(2);
        } finally {
            link.shutdownNow();
            faulty.shutdownNow();
            metrics.close();
        }
    }
}
