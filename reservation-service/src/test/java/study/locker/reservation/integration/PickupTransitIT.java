package study.locker.reservation.integration;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import study.locker.common.PickupTransit;

import static org.assertj.core.api.Assertions.*;
import static study.locker.reservation.integration.GrpcFailureAssert.assertThatGrpcFailure;

class PickupTransitIT extends ReservationFixture {
    @Test
    void rotationPreservesReplaysLiveCodesAndQueuedMail() {
        var request = cmd("CENTRAL-S1");
        var booking = client(customer).reserve(request);
        String code = deposit(booking.getId());
        vault.rotate("command-fingerprint");
        vault.rotate("pickup-verifier");
        vault.rotate("pickup-mail");
        assertThat(client(customer).reserve(request)).isEqualTo(booking);
        assertThat(lastCode()).isEqualTo(code);
        assertThat(client(customer).collect(cmd(booking.getId()).toBuilder().setCode(code).build()).getState())
                .isEqualTo("COLLECTED");
    }

    @Test
    void legacyEnvelopeAuthenticatesOriginalEventAndRolesRestrictKeyUse() {
        String event = "20260000-0000-4000-8000-000000000002";
        String envelope = "nChaaJofWWOgETsebPl8gHxPFt6buesv3TKgQgyYSusKiGEh";
        var reader = vault.transit("notifications");
        assertThat(reader.open(event, envelope)).isEqualTo("01234567");
        assertThatThrownBy(() -> reader.open("different-event", envelope)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.seal(event, "01234567")).isInstanceOf(PickupTransit.Unavailable.class);
        assertThatThrownBy(() -> secrets.open(event, envelope)).isInstanceOf(PickupTransit.Unavailable.class);
    }

    @Test
    void vaultOutageRollsBackCommandAndRetrySucceedsAfterRecovery() {
        var booking = reserve();
        String code = deposit(booking.getId());
        var request = cmd(booking.getId()).toBuilder().setCode(code).build();
        var docker = vault.getDockerClient();
        docker.pauseContainerCmd(vault.getContainerId()).exec();
        try {
            assertThatGrpcFailure(() -> client(customer).withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                    .collect(request)).hasCode(Status.Code.UNAVAILABLE);
            assertThat(db.sql("select attempts from booking").query(Integer.class).single()).isZero();
            assertRowCounts(1, 2, 3);
        } finally {
            docker.unpauseContainerCmd(vault.getContainerId()).exec();
        }
        assertThat(client(customer).collect(request).getState()).isEqualTo("COLLECTED");
    }
}
