package study.locker.reservation.domain.model;

import java.time.*;

public record PickupCode(String verifier, Instant expiresAt, int attempts, int maxAttempts) {
    public static PickupCode issue(String verifier, Instant now, Duration ttl, int maxAttempts) {
        return new PickupCode(verifier, now.plus(ttl), 0, maxAttempts);
    }

    boolean usableAt(Instant now) {
        return attempts < maxAttempts && now.isBefore(expiresAt);
    }

    PickupCode failedAttempt() {
        return new PickupCode(verifier, expiresAt, attempts + 1, maxAttempts);
    }
}
