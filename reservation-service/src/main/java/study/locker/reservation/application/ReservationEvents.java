package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;

import java.time.Instant;

public interface ReservationEvents {
    void lifecycle(Reservation.View view, String type, Instant now);

    void pickupMail(Reservation booking, PickupCodes.Issue issued);
}
