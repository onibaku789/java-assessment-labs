package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;

import java.util.List;

public interface ReservationAccess {
    record Request(String operation, String requestId, String target, String fingerprint) {
        public CommandHandler.Command commandFor(String subject) {
            return new CommandHandler.Command(subject, operation, ReservationQueries.parseId(requestId), fingerprint);
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
