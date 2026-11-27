package study.locker.reservation.adapter.persistence;

import jakarta.persistence.*;

import study.locker.reservation.domain.model.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
class BookingRow {
    protected BookingRow() {}

    @Id UUID id;
    String compartment;

    @Column(name = "owner_sub")
    String owner;

    String email, state, verifier;

    @Column(name = "expires_at")
    Instant expires;

    int attempts;

    @Column(name = "pickup_max_attempts")
    int maxAttempts;

    Reservation domain() {
        var pickup =
                verifier == null ? null : new PickupCode(verifier, expires, attempts, maxAttempts);
        return new Reservation(
                id, compartment, owner, email, Reservation.State.valueOf(state), pickup);
    }

    void apply(Reservation booking) {
        state = booking.view().state().name();
        var code = booking.pickup();
        verifier = code.map(PickupCode::verifier).orElse(null);
        expires = code.map(PickupCode::expiresAt).orElse(null);
        code.ifPresent(active -> maxAttempts = active.maxAttempts());
        attempts = code.map(PickupCode::attempts).orElse(0);
    }
}
