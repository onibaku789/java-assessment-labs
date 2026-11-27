package study.locker.reservation.application;

import study.locker.reservation.domain.model.ReservationFailure;

public record Caller(String subject, String email, boolean emailVerified) {
    public String verifiedEmail() {
        if (!emailVerified || email == null || !email.matches("[^\\s@,;<>]+@[^\\s@,;<>]+"))
            throw forbidden("Verified customer email required");
        return email;
    }

    private static ReservationFailure forbidden(String message) {
        return new ReservationFailure(ReservationFailure.Reason.FORBIDDEN, message);
    }
}
