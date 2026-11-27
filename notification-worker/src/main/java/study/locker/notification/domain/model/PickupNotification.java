package study.locker.notification.domain.model;

import java.time.Instant;

public record PickupNotification(
        int version,
        String eventId,
        String reservationId,
        String recipient,
        String expiresAt,
        String envelope) {

    public boolean expiredAt(Instant now) {
        return !now.isBefore(Instant.parse(expiresAt));
    }
}
