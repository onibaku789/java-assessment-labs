package study.locker.reservation.application;

import study.locker.reservation.domain.model.*;

import java.time.Instant;
import java.util.UUID;

public interface PickupCodes {
    record Issue(UUID eventId, PickupCode pickup, String envelope) {}

    Issue issue(Reservation reservation, Instant now);

    boolean matches(UUID reservation, String candidate, String verifier);
}
