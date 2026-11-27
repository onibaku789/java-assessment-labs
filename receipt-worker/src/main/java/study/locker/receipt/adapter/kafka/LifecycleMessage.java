package study.locker.receipt.adapter.kafka;

import jakarta.validation.constraints.*;

import study.locker.delivery.EventTimestamp;
import study.locker.delivery.EventUuid;
import study.locker.receipt.domain.model.Receipt;

import java.util.UUID;

record LifecycleMessage(
        @NotNull @Min(1) @Max(1) Integer version,
        @EventUuid String eventId,
        @EventUuid String reservationId,
        @NotBlank String compartmentId,
        @NotBlank String type,
        @EventTimestamp String occurredAt) {
    Receipt toReceipt() {
        return new Receipt(
                version,
                UUID.fromString(eventId),
                UUID.fromString(reservationId),
                compartmentId,
                type,
                occurredAt);
    }
}
