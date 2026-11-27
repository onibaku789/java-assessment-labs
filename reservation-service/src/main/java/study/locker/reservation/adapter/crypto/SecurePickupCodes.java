package study.locker.reservation.adapter.crypto;

import study.locker.common.PickupSecrets;
import study.locker.reservation.application.PickupCodes;
import study.locker.reservation.domain.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SecurePickupCodes(PickupSecrets secrets, Duration ttl, int maxAttempts)
        implements PickupCodes {
    public Issue issue(Reservation booking, Instant now) {
        UUID id = booking.view().id();
        var previous = booking.pickup();
        String candidate;
        do {
            candidate = secrets.code();
        } while (previous.isPresent() && matches(id, candidate, previous.get().verifier()));
        UUID event = UUID.randomUUID();
        var code =
                PickupCode.issue(
                        secrets.fingerprint("pickup:" + id + ":" + candidate),
                        now,
                        ttl,
                        maxAttempts);
        return new Issue(event, code, secrets.seal(event.toString(), candidate));
    }

    public boolean matches(UUID id, String candidate, String verifier) {
        return secrets.matches(id.toString(), candidate, verifier);
    }
}
