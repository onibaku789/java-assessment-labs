package study.locker.edge.application;

import java.util.List;

public interface LockerOperations {
    record Slot(String id, String site, String size) {
    }

    record Reservation(String id, String compartmentId, String state) {
    }

    List<Slot> available(String site, String size);

    Reservation get(String reservationId);

    Reservation reserve(String requestId, String compartmentId);

    Reservation cancel(String requestId, String reservationId);

    Reservation deposit(String requestId, String reservationId);

    Reservation reissue(String requestId, String reservationId);

    Reservation collect(String requestId, String reservationId, String code);
}
