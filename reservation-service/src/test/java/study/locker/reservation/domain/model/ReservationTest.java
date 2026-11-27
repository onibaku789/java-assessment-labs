package study.locker.reservation.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

class ReservationTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Reservation booking =
            Reservation.reserve("NORTH-L1", "customer", "customer@example.test");

    @Test
    void onlyTheOwnerCanCancelAndOnlyBeforeDeposit() {
        assertThatThrownBy(() -> booking.ensureOwner("someone-else"))
                .isInstanceOf(ReservationFailure.class);
        booking.ensureOwner("customer");
        booking.cancel();
        assertThat(booking.view().state()).isEqualTo(Reservation.State.CANCELLED);
        assertThatThrownBy(
                        () ->
                                booking.deposit(
                                        PickupCode.issue(
                                                "verifier", now, Duration.ofMinutes(15), 5)))
                .isInstanceOf(ReservationFailure.class);
    }

    @Test
    void reissuingAfterFiveFailuresAllowsOneCollectionWithTheNewCode() {
        booking.deposit(PickupCode.issue("old", now, Duration.ofMinutes(15), 5));
        for (int i = 0; i < 5; i++)
            assertThat(booking.collect(now, verifier -> false)).contains("Invalid pickup code");
        assertThat(booking.collect(now, "old"::equals)).contains("Pickup code locked or expired");

        booking.reissue(PickupCode.issue("new", now, Duration.ofMinutes(15), 5));
        assertThat(booking.collect(now, "old"::equals)).contains("Invalid pickup code");
        assertThat(booking.collect(now.plusSeconds(899), "new"::equals)).isEmpty();
        assertThat(booking.view().state()).isEqualTo(Reservation.State.COLLECTED);
        assertThat(booking.pickup()).isEmpty();
        assertThatThrownBy(() -> booking.collect(now.plusSeconds(900), "new"::equals))
                .isInstanceOf(ReservationFailure.class);
    }

    @Test
    void reissueSnapshotsANewLifetimeAndFailureLimit() {
        booking.deposit(PickupCode.issue("old", now, Duration.ofSeconds(10), 1));
        assertThat(booking.collect(now, verifier -> false)).contains("Invalid pickup code");
        assertThat(booking.collect(now, "old"::equals)).contains("Pickup code locked or expired");
        booking.reissue(PickupCode.issue("new", now, Duration.ofSeconds(3), 2));
        assertThat(booking.pickup().orElseThrow().attempts()).isZero();
        assertThat(booking.collect(now.plusSeconds(2), verifier -> false))
                .contains("Invalid pickup code");
        assertThat(booking.collect(now.plusSeconds(3), "new"::equals))
                .contains("Pickup code locked or expired");
    }

    @Test
    void codeExpiresAtTheFifteenMinuteBoundary() {
        booking.deposit(PickupCode.issue("original", now, Duration.ofMinutes(15), 5));
        assertThat(booking.collect(now.plusSeconds(900), "original"::equals))
                .contains("Pickup code locked or expired");
        assertThat(booking.view().state()).isEqualTo(Reservation.State.DEPOSITED);
    }
}
