package study.locker.reservation.application;

import study.locker.reservation.domain.model.Reservation;
import study.locker.reservation.domain.model.ReservationFailure;

import java.util.UUID;
import java.util.function.Supplier;

public interface CommandHandler {
    record Command(String operation, UUID requestId, String fingerprint, Supplier<Result> work) {
    }

    record Result(Reservation.View view, String error) {
        public Reservation.View requireSuccess() {
            if (error != null) throw new ReservationFailure(ReservationFailure.Reason.INVALID_STATE, error);
            return view;
        }
    }

    record Execution(Result result, boolean replayed) {
    }

    Execution execute(Caller caller, Command command);
}
