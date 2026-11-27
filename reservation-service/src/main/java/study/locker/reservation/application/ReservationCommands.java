package study.locker.reservation.application;

import static study.locker.reservation.domain.model.ReservationFailure.Reason.*;

import study.locker.reservation.domain.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

public record ReservationCommands(ReservationStore store, Directory directory, PickupCodes codes,
                                  ReservationEvents events, Clock clock) {
    public CommandHandler.Result reserve(Caller caller, String target) {
        String email = caller.verifiedEmail();
        directory.requireCompartment(target);
        var booking = Reservation.reserve(target, caller.subject(), email);
        if (!store.insertIfAvailable(booking)) throw new ReservationFailure(OCCUPIED, "Compartment already reserved");
        events.lifecycle(booking.view(), "RESERVED", clock.instant());
        return new CommandHandler.Result(booking.view(), null);
    }

    public CommandHandler.Result cancel(Caller caller, String target) {
        var booking = owned(caller, target);
        booking.cancel();
        return save(booking, clock.instant(), "CANCELLED", null);
    }

    public CommandHandler.Result deposit(String target) {
        var booking = locked(target);
        return issuePickupCode(booking, booking::deposit, "DEPOSITED");
    }

    public CommandHandler.Result reissue(Caller caller, String target) {
        var booking = owned(caller, target);
        return issuePickupCode(booking, booking::reissue, "CODE_REISSUED");
    }

    public CommandHandler.Result collect(Caller caller, String target, String code) {
        var booking = owned(caller, target);
        var now = clock.instant();
        var id = booking.view().id();
        var error = booking.collect(now, verifier -> codes.matches(id, code, verifier));
        return save(booking, now, "COLLECTED", error.orElse(null));
    }

    private CommandHandler.Result issuePickupCode(Reservation booking, Consumer<PickupCode> applyCode, String event) {
        var now = clock.instant();
        var issued = codes.issue(booking, now);
        applyCode.accept(issued.pickup());
        events.pickupMail(booking, issued);
        return save(booking, now, event, null);
    }

    private Reservation owned(Caller caller, String target) {
        var booking = locked(target);
        booking.ensureOwner(caller.subject());
        return booking;
    }

    private Reservation locked(String target) {
        return store.find(ReservationQueries.parseId(target), true).orElseThrow(ReservationFailure::missing);
    }

    private CommandHandler.Result save(Reservation booking, Instant now, String event, String error) {
        store.update(booking);
        var view = booking.view();
        if (error == null) events.lifecycle(view, event, now);
        return new CommandHandler.Result(view, error);
    }
}
