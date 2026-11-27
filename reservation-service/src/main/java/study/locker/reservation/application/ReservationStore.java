package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;

import java.util.*;

public interface ReservationStore {
    boolean insertIfAvailable(Reservation booking);

    Optional<Reservation> find(UUID id, boolean lockForUpdate);

    void update(Reservation reservation);

    Set<String> occupied(List<String> candidates);
}
