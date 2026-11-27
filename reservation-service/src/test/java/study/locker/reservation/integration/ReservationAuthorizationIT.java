package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import study.locker.common.*;
import study.locker.reservation.adapter.grpcserver.ReservationEndpoint;
import study.locker.reservation.application.*;
import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import java.util.*;
import java.util.concurrent.*;

class ReservationAuthorizationIT extends ReservationFixture {
    @Autowired
    ReservationAccess access;

    @Test
    void validatesCommandTargetsAfterAuthorizationAndUuidParsing() {
        var malformed = cmd("").toBuilder().setRequestId("invalid").build();
        assertThatThrownBy(() -> client(courier).reserve(malformed)).hasMessageContaining(
                "PERMISSION_DENIED: Missing scope locker.reserve");
        assertThatThrownBy(() -> client(customer).reserve(malformed)).hasMessageContaining(
                "INVALID_ARGUMENT: Expected UUID");

        for (String invalid : List.of("", " \t", "x".repeat(101))) {
            assertThatThrownBy(() -> client(customer).reserve(cmd(invalid))).hasMessageContaining(
                    "INVALID_ARGUMENT: A valid ID is required");
        }
        assertRowCounts(0, 0, 0);
        String longest = "x".repeat(100);
        assertThat(client(customer).reserve(cmd(longest)).getCompartmentId()).isEqualTo(longest);
    }

    @Test
    void customersCannotReadOrCollectAnotherCustomersReservation() {
        String id = reserve().getId();
        var bob = ReservationTestBeans.actor("bob", "locker.read locker.reserve");
        assertThatThrownBy(() -> client(bob).get(Key.newBuilder().setId(id).build())).hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> execute(bob, Action.CANCEL, cmd(id))).hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> execute(customer, Action.DEPOSIT, cmd(id))).hasMessageContaining("PERMISSION_DENIED");
    }

    @Test
    void authorizeEveryRetryAndCheckVerifiedEmailOnlyForFreshReservations() {
        var request = cmd("CENTRAL-S1");
        Booking original = client(customer).reserve(request);
        assertThat(client(channel, "alice-unverified").reserve(request)).isEqualTo(original);
        assertThatThrownBy(() -> client(channel, "alice-no-scope").reserve(request)).hasMessageContaining(
                "PERMISSION_DENIED: Missing scope locker.reserve");
        assertThatThrownBy(() -> client(channel, "alice-unverified").reserve(cmd("CENTRAL-S2"))).hasMessageContaining(
                "Verified customer email required");
        assertThatThrownBy(() -> client(channel, "courier-no-read").get(
                Key.newBuilder().setId(original.getId()).build())).hasMessageContaining(
                "PERMISSION_DENIED: Missing scope locker.read");
        assertThatThrownBy(() -> ReservationsGrpc.newBlockingStub(channel)
                .available(Search.getDefaultInstance())).hasMessageContaining("UNAUTHENTICATED");
        assertRowCounts(1, 1, 1);
    }

    @Test
    void twoCallersOnOneGrpcThreadRestoreThePreviousIdentityEvenAfterDenial() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            var previous = new TestingAuthenticationToken("outer", "unused");
            worker.submit(() -> SecurityContextHolder.getContext().setAuthentication(previous)).get();
            var localServer = ServerBuilder.forPort(0).executor(worker).addService(
                    ServerInterceptors.intercept(new ReservationEndpoint(access, secrets),
                            new RpcIdentity(lockerDecoder))).build().start();
            var localChannel = Rpc.channel("127.0.0.1:" + localServer.getPort());
            try {
                var alice = client(localChannel, "alice");
                var driver = client(localChannel, "courier");
                var reservation = alice.reserve(cmd("CENTRAL-S1"));
                var key = Key.newBuilder().setId(reservation.getId()).build();
                assertThat(driver.get(key)).isEqualTo(reservation);
                assertThat(worker.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get()).isSameAs(
                        previous);
                assertThatThrownBy(() -> alice.deposit(cmd(reservation.getId()))).hasMessageContaining(
                        "PERMISSION_DENIED: Missing scope locker.deposit");
                assertThat(worker.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get()).isSameAs(
                        previous);
                assertRowCounts(1, 1, 1);
            } finally {
                localChannel.shutdownNow();
                localServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
