package study.locker.receipt.domain.model;

import java.util.UUID;

public record Receipt(
        int version,
        UUID eventId,
        UUID reservationId,
        String compartmentId,
        String type,
        String occurredAt) {}
