package study.locker.reservation.integration;

import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static io.grpc.Status.Code.NOT_FOUND;
import static io.grpc.Status.Code.PERMISSION_DENIED;
import static io.grpc.Status.Code.UNAUTHENTICATED;
import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.reservation.integration.GrpcFailureAssert.assertThatGrpcFailure;

import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import study.locker.common.*;
import study.locker.wire.*;

import java.util.*;
import java.util.concurrent.*;

class ReservationAuthorizationIT extends ReservationFixture {

    @Test
    void invalidBearerTokenIsUnauthenticated() {
        assertThatGrpcFailure(() -> client(channel, "invalid-token").available(Search.getDefaultInstance()))
                .hasCode(UNAUTHENTICATED);
        assertRowCounts(0, 0, 0);
    }

    @Test
    void validatesCommandTargetsAfterAuthorizationAndUuidParsing() {
        var malformed = cmd("").toBuilder().setRequestId("invalid").build();
        assertThatGrpcFailure(() -> client(courier).reserve(malformed))
                .hasCode(PERMISSION_DENIED)
                .hasDescriptionContaining("Access denied");
        assertThatGrpcFailure(() -> client(customer).reserve(malformed))
                .hasCode(INVALID_ARGUMENT)
                .hasDescriptionContaining("Expected UUID");

        for (String invalid : List.of("", " \t", "x".repeat(101))) {
            assertThatGrpcFailure(() -> client(customer).reserve(cmd(invalid)))
                    .hasCode(INVALID_ARGUMENT)
                    .hasDescriptionContaining("A valid ID is required");
        }
        assertRowCounts(0, 0, 0);
        String longest = "x".repeat(100);
        assertThat(client(customer).reserve(cmd(longest)).getCompartmentId()).isEqualTo(longest);
    }

    @Test
    void customersCannotReadOrCollectAnotherCustomersReservation() {
        String id = reserve().getId();
        var bob = ReservationTestBeans.actor("bob", "locker.read locker.reserve");
        assertThatGrpcFailure(() -> client(bob).get(Key.newBuilder().setId(id).build())).hasCode(NOT_FOUND);
        assertThatGrpcFailure(() -> client(bob).cancel(cmd(id))).hasCode(NOT_FOUND);
        assertThatGrpcFailure(() -> client(customer).deposit(cmd(id))).hasCode(PERMISSION_DENIED);
    }

    @Test
    void authorizeEveryRetryAndCheckVerifiedEmailOnlyForFreshReservations() {
        var request = cmd("CENTRAL-S1");
        Booking original = client(customer).reserve(request);
        assertThat(client(channel, "alice-unverified").reserve(request)).isEqualTo(original);
        assertThatGrpcFailure(() -> client(channel, "alice-no-scope").reserve(request))
                .hasCode(PERMISSION_DENIED)
                .hasDescriptionContaining("Access denied");
        assertThatGrpcFailure(() -> client(channel, "alice-unverified").reserve(cmd("CENTRAL-S2")))
                .hasCode(PERMISSION_DENIED)
                .hasDescriptionContaining("Verified customer email required");
        assertThatGrpcFailure(() -> client(channel, "courier-no-read").get(Key.newBuilder().setId(original.getId()).build()))
                .hasCode(PERMISSION_DENIED)
                .hasDescriptionContaining("Access denied");
        assertThatGrpcFailure(() -> ReservationsGrpc.newBlockingStub(channel).available(Search.getDefaultInstance()))
                .hasCode(UNAUTHENTICATED);
        assertRowCounts(1, 1, 1);
    }

    @Test
    void twoCallersOnOneGrpcThreadClearAuthenticationAfterSuccessAndDenial() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            var previous = new TestingAuthenticationToken("outer", "unused");
            worker.submit(() -> SecurityContextHolder.getContext().setAuthentication(previous)).get();
            var localServer = ServerBuilder.forPort(0).executor(worker).addService(server.reservations()).build()
                    .start();
            var localChannel = Rpc.channel("127.0.0.1:" + localServer.getPort());
            try {
                var alice = client(localChannel, "alice");
                var driver = client(localChannel, "courier");
                var reservation = alice.reserve(cmd("CENTRAL-S1"));
                var key = Key.newBuilder().setId(reservation.getId()).build();
                assertThat(driver.get(key)).isEqualTo(reservation);
                assertThat(worker.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get()).isNull();
                assertThatGrpcFailure(() -> alice.deposit(cmd(reservation.getId())))
                        .hasCode(PERMISSION_DENIED)
                        .hasDescriptionContaining("Access denied");
                assertThat(worker.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get()).isNull();
                assertRowCounts(1, 1, 1);
            } finally {
                localChannel.shutdownNow();
                localServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
