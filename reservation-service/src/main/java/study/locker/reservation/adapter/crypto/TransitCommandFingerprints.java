package study.locker.reservation.adapter.crypto;

import study.locker.common.PickupTransit;
import study.locker.reservation.application.CommandFingerprints;

import static study.locker.common.PickupTransit.MacKey.COMMAND;

public record TransitCommandFingerprints(PickupTransit transit) implements CommandFingerprints {
    public String create(String input) {
        return transit.fingerprint(COMMAND, input);
    }

    public boolean matches(String input, String stored) {
        return transit.verifies(COMMAND, input, stored);
    }
}
