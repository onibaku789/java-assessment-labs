package study.locker.reservation.adapter.persistence;

import org.springframework.stereotype.Repository;

import study.locker.reservation.application.ReservationStore;
import study.locker.reservation.domain.model.Reservation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JpaReservationStore implements ReservationStore {
    private final BookingRows rows;

    JpaReservationStore(BookingRows rows) {
        this.rows = rows;
    }

    @Override
    public boolean insertIfAvailable(Reservation reservation) {
        var view = reservation.view();
        return rows.insertIfAvailable(
                        view.id(), view.compartment(), reservation.owner(), reservation.email())
                == 1;
    }

    @Override
    public Optional<Reservation> find(UUID id, boolean lockForUpdate) {
        var row = lockForUpdate ? rows.findForUpdateById(id) : rows.findById(id);
        return row.map(BookingRow::domain);
    }

    @Override
    public void update(Reservation reservation) {
        rows.getReferenceById(reservation.view().id()).apply(reservation);
    }

    @Override
    public Set<String> occupied(List<String> candidates) {
        return rows.findOccupiedCompartments(candidates);
    }
}
