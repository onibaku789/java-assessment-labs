package study.locker.reservation.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class Reservation {
    public enum State {
        RESERVED,
        DEPOSITED,
        CANCELLED,
        COLLECTED
    }

    public record View(UUID id, String compartment, State state) {}

    private final UUID id;
    private final String compartment, owner, email;
    private State state;
    private PickupCode pickup;

    public Reservation(
            UUID id,
            String compartment,
            String owner,
            String email,
            State state,
            PickupCode pickup) {
        this.id = id;
        this.compartment = compartment;
        this.owner = owner;
        this.email = email;
        this.state = state;
        this.pickup = pickup;
    }

    public static Reservation reserve(String compartment, String owner, String email) {
        return new Reservation(UUID.randomUUID(), compartment, owner, email, State.RESERVED, null);
    }

    public View view() {
        return new View(id, compartment, state);
    }

    public String owner() {
        return owner;
    }

    public String email() {
        return email;
    }

    public Optional<PickupCode> pickup() {
        return Optional.ofNullable(pickup);
    }

    public void ensureOwner(String subject) {
        if (!owner.equals(subject)) throw ReservationFailure.missing();
    }

    public void cancel() {
        require(State.RESERVED);
        state = State.CANCELLED;
    }

    public void deposit(PickupCode fresh) {
        require(State.RESERVED);
        pickup = fresh;
        state = State.DEPOSITED;
    }

    public void reissue(PickupCode fresh) {
        require(State.DEPOSITED);
        pickup = fresh;
    }

    public Optional<String> collect(Instant now, Predicate<String> matchesVerifier) {
        require(State.DEPOSITED);
        if (!pickup.usableAt(now)) return Optional.of("Pickup code locked or expired");
        if (!matchesVerifier.test(pickup.verifier())) {
            pickup = pickup.failedAttempt();
            return Optional.of("Invalid pickup code");
        }
        pickup = null;
        state = State.COLLECTED;
        return Optional.empty();
    }

    private void require(State expected) {
        if (state != expected)
            throw new ReservationFailure(
                    ReservationFailure.Reason.INVALID_STATE, "Reservation must be " + expected);
    }
}
