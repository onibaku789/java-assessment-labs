package study.locker.reservation.adapter.persistence;

import jakarta.persistence.*;

import study.locker.reservation.application.PublishOutbox;

import java.util.UUID;

@Entity
@Table(name = "outbox")
class OutboxRow {
    protected OutboxRow() {}

    OutboxRow(String topic, UUID reservationId, UUID eventId, String payload) {
        this.topic = topic;
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.payload = payload;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long seq;

    @Column(name = "event_id")
    UUID eventId;

    @Column(name = "reservation_id")
    UUID reservationId;

    @Column(name = "trace_parent", insertable = false, updatable = false)
    String traceParent;

    String topic, payload;

    PublishOutbox.Message message() {
        return new PublishOutbox.Message(seq, topic, reservationId, eventId, payload, traceParent);
    }
}
