package study.locker.reservation.domain.model;

public final class ReservationFailure extends RuntimeException {
    public enum Reason {
        INVALID_INPUT,
        UNKNOWN_RESERVATION,
        OCCUPIED,
        REUSED_KEY,
        FORBIDDEN,
        INVALID_STATE
    }

    private final Reason reason;

    public ReservationFailure(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static ReservationFailure missing() {
        return new ReservationFailure(Reason.UNKNOWN_RESERVATION, "Unknown reservation");
    }
}
