package study.locker.reservation.adapter.crypto;

import study.locker.common.PickupTransit;
import study.locker.reservation.application.PickupCodes;
import study.locker.reservation.domain.model.*;

import java.security.SecureRandom;
import java.util.Locale;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SecurePickupCodes(PickupTransit secrets, Duration ttl, int maxAttempts)
        implements PickupCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    public Issue issue(Reservation booking, Instant now) {
        UUID id = booking.view().id();
        var previous = booking.pickup();
        String candidate;
        do {
            candidate = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        } while (previous.isPresent() && matches(id, candidate, previous.get().verifier()));
        UUID event = UUID.randomUUID();
        var code =
                PickupCode.issue(
                        secrets.fingerprint(PickupTransit.MacKey.PICKUP, "pickup:" + id + ":" + candidate),
                        now,
                        ttl,
                        maxAttempts);
        return new Issue(event, code, secrets.seal(event.toString(), candidate));
    }

    public boolean matches(UUID id, String candidate, String verifier) {
        return secrets.verifies(PickupTransit.MacKey.PICKUP, "pickup:" + id + ":" + candidate, verifier);
    }
}
