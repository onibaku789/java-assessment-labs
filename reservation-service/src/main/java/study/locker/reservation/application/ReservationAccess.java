package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;

import java.util.List;
import java.util.UUID;

public interface ReservationAccess {
    record Request(String operation, String requestId, String target, String fingerprint) {
        public UUID parsedId() {
            return ReservationQueries.parseId(requestId);
        }
    }

    List<Directory.Slot> available(String site, String size);

    Reservation.View get(String id);

    Reservation.View reserve(Request request);

    Reservation.View cancel(Request request);

    Reservation.View deposit(Request request);

    Reservation.View reissue(Request request);

    Reservation.View collect(Request request, String code);
}
