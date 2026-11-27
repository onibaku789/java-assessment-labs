package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import study.locker.common.*;
import study.locker.reservation.adapter.grpcserver.ReservationEndpoint;
import study.locker.reservation.application.*;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

class LostCommandReplyIT extends ReservationFixture {
    @Autowired
    ReservationAccess access;

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
        var faulty = Rpc.serve(0, new ReservationEndpoint(access, secrets), new RpcIdentity(lockerDecoder),
                new DropFirstReplyPerMethod());
        var link = Rpc.channel("127.0.0.1:" + faulty.getPort());
        var metrics = new SimpleMeterRegistry();
        var guard = new RpcGuard("reservations", Duration.ofSeconds(2),
                new RpcGuard.Settings(4, 20, 10, 50, Duration.ofSeconds(10), 2, Duration.ZERO, true), 4, metrics);
        try {
            var gateway = client(link, customer.getSubject());
            var reserve = cmd("CENTRAL-S1");
            Booking booking = guard.call(true, limit -> gateway.withDeadline(limit).reserve(reserve));
            assertRowCounts(1, 1, 1);
            deposit(booking.getId());
            var reject = cmd(booking.getId()).toBuilder().setCode("wrong").build();
            assertThatThrownBy(
                    () -> guard.call(true, limit -> gateway.withDeadline(limit).collect(reject))).hasMessageContaining(
                    "Invalid pickup code");
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
