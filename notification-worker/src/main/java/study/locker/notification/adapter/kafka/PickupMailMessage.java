package study.locker.notification.adapter.kafka;

import jakarta.validation.constraints.*;

import study.locker.delivery.EventTimestamp;
import study.locker.delivery.EventUuid;
import study.locker.notification.domain.model.PickupNotification;

record PickupMailMessage(
        @NotNull @Min(1) @Max(1) Integer version,
        @EventUuid String eventId,
        @EventUuid String reservationId,
        @NotBlank @Email String recipient,
        @EventTimestamp String expiresAt,
        @NotBlank String envelope) {
    PickupNotification toNotification() {
        return new PickupNotification(
                version, eventId, reservationId, recipient, expiresAt, envelope);
    }
}
