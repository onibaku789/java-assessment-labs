package study.locker.reservation.adapter.persistence;

import org.springframework.stereotype.Component;

import study.locker.reservation.application.PickupCodes;
import study.locker.reservation.application.ReservationEvents;
import study.locker.reservation.domain.model.Reservation;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
class ReservationEventWriter implements ReservationEvents {
    private final OutboxRows rows;
    private final ObjectMapper json;

    ReservationEventWriter(OutboxRows rows, ObjectMapper json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void lifecycle(Reservation.View view, String type, Instant now) {
        var event = UUID.randomUUID();
        append(
                "locker.lifecycle.v1",
                view.id(),
                event,
                Map.of(
                        "version",
                        1,
                        "eventId",
                        event.toString(),
                        "reservationId",
                        view.id().toString(),
                        "compartmentId",
                        view.compartment(),
                        "type",
                        type,
                        "occurredAt",
                        now.toString()));
    }

    @Override
    public void pickupMail(Reservation booking, PickupCodes.Issue issued) {
        UUID id = booking.view().id(), event = issued.eventId();
        append(
                "locker.pickup-mail.v1",
                id,
                event,
                Map.of(
                        "version",
                        1,
                        "eventId",
                        event.toString(),
                        "reservationId",
                        id.toString(),
                        "recipient",
                        booking.email(),
                        "expiresAt",
                        issued.pickup().expiresAt().toString(),
                        "envelope",
                        issued.envelope()));
    }

    private void append(String topic, UUID reservation, UUID event, Map<String, Object> message) {
        rows.save(new OutboxRow(topic, reservation, event, json.writeValueAsString(message)));
    }
}
