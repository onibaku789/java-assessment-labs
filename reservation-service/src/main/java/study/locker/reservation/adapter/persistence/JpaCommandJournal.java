package study.locker.reservation.adapter.persistence;

import com.google.protobuf.InvalidProtocolBufferException;

import org.springframework.stereotype.Repository;

import study.locker.reservation.application.CommandHandler.Result;
import study.locker.reservation.application.CommandJournal;
import study.locker.reservation.domain.model.Reservation;
import study.locker.wire.Booking;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCommandJournal implements CommandJournal {
    private final CommandRows rows;

    JpaCommandJournal(CommandRows rows) {
        this.rows = rows;
    }

    @Override
    public void lock(String subject, UUID request) {
        rows.lockRequest(subject + ":" + request);
    }

    @Override
    public Optional<Entry> find(String subject, UUID request) {
        return rows.findById(new CommandRow.Key(subject, request)).map(this::decode);
    }

    private Entry decode(CommandRow row) {
        try {
            Booking booking = Booking.parseFrom(row.result);
            var view =
                    new Reservation.View(
                            UUID.fromString(booking.getId()),
                            booking.getCompartmentId(),
                            Reservation.State.valueOf(booking.getState()));
            return new Entry(row.fingerprint, new Result(view, row.error));
        } catch (InvalidProtocolBufferException ex) {
            throw new IllegalStateException("Unreadable command journal entry", ex);
        }
    }

    @Override
    public void save(String subject, UUID request, Entry entry) {
        var view = entry.result().view();
        byte[] snapshot =
                Booking.newBuilder()
                        .setId(view.id().toString())
                        .setCompartmentId(view.compartment())
                        .setState(view.state().name())
                        .build()
                        .toByteArray();
        rows.save(
                new CommandRow(
                        new CommandRow.Key(subject, request),
                        entry.fingerprint(),
                        snapshot,
                        entry.result().error()));
    }
}
