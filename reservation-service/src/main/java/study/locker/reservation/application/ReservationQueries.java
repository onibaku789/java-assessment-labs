package study.locker.reservation.application;

import study.locker.reservation.domain.model.*;

import java.util.*;

public record ReservationQueries(ReservationStore store, Directory directory) {
    public List<Directory.Slot> available(String site, String size) {
        var candidates = directory.search(site, size);
        if (candidates.isEmpty()) return List.of();
        var occupied = store.occupied(candidates.stream().map(Directory.Slot::id).toList());
        return candidates.stream().filter(slot -> !occupied.contains(slot.id())).toList();
    }

    public Reservation.View get(Optional<String> requiredOwner, String id) {
        var booking = store.find(parseId(id), false).orElseThrow(ReservationFailure::missing);
        requiredOwner.ifPresent(booking::ensureOwner);
        return booking.view();
    }

    static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ReservationFailure(ReservationFailure.Reason.INVALID_INPUT, "Expected UUID");
        }
    }
}
