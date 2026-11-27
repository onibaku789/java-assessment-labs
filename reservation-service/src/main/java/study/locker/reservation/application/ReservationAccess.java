package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;

import java.util.List;
import java.util.UUID;

public interface ReservationAccess {
    record Request(String requestId, String target, String candidate, String fingerprint) {
        public UUID parsedId() {
            return ReservationQueries.parseId(requestId);
        }
    }

    List<Directory.Slot> available(String site, String size);

    Reservation.View get(String id);

    Reservation.View execute(ReservationCommands.Action action, Request request);
}
