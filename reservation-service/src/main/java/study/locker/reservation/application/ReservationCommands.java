package study.locker.reservation.application;

import static study.locker.reservation.domain.model.ReservationFailure.Reason.*;

import study.locker.reservation.domain.model.*;

import java.time.Clock;

public record ReservationCommands(ReservationStore store, Directory directory, PickupCodes codes,
                                  ReservationEvents events, Clock clock) {
    public enum Action {
        RESERVE, CANCEL, DEPOSIT, REISSUE, COLLECT
    }

    public CommandHandler.Result execute(Caller caller, Action action, String target, String candidate) {
        return action == Action.RESERVE ? reserve(caller, target) : transition(caller, action, target, candidate);
    }

    private CommandHandler.Result reserve(Caller caller, String target) {
        String email = caller.verifiedEmail();
        directory.requireCompartment(target);
        var booking = Reservation.reserve(target, caller.subject(), email);
        if (!store.insertIfAvailable(booking)) throw new ReservationFailure(OCCUPIED, "Compartment already reserved");
        events.lifecycle(booking.view(), "RESERVED", clock.instant());
        return new CommandHandler.Result(booking.view(), null);
    }

    private CommandHandler.Result transition(Caller caller, Action action, String target, String candidate) {
        var id = ReservationQueries.parseId(target);
        var booking = store.find(id, true).orElseThrow(ReservationFailure::missing);
        if (action != Action.DEPOSIT) booking.ensureOwner(caller.subject());
        var now = clock.instant();
        String error = null;
        switch (action) {
            case CANCEL -> booking.cancel();
            case DEPOSIT, REISSUE -> {
                issuePickup(booking, action, now);
            }
            case COLLECT ->
                    error = booking.collect(now, verifier -> codes.matches(id, candidate, verifier)).orElse(null);
            default -> throw new IllegalStateException("Unexpected transition");
        }
        store.update(booking);
        var view = booking.view();
        if (error == null)
            events.lifecycle(view, action == Action.REISSUE ? "CODE_REISSUED" : view.state().name(), now);
        return new CommandHandler.Result(view, error);
    }

    private void issuePickup(Reservation booking, Action action, java.time.Instant now) {
        var issued = codes.issue(booking, now);
        if (action == Action.DEPOSIT) booking.deposit(issued.pickup());
        else booking.reissue(issued.pickup());
        events.pickupMail(booking, issued);
    }
}
