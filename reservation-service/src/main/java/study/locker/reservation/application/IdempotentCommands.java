package study.locker.reservation.application;

import study.locker.reservation.domain.model.ReservationFailure;

public record IdempotentCommands(CommandJournal journal) implements CommandHandler {
    @Override
    public Execution execute(Caller caller, Command command) {
        var subject = caller.subject();
        var key = command.requestId();
        journal.lock(subject, key);
        var saved = journal.find(subject, key);
        if (saved.isPresent()) {
            var entry = saved.get();
            if (!entry.fingerprint().equals(command.fingerprint()))
                throw new ReservationFailure(ReservationFailure.Reason.REUSED_KEY,
                        "Request ID already used for different input");
            return new Execution(entry.result(), true);
        }
        var result = command.work().get();
        journal.save(subject, key, new CommandJournal.Entry(command.fingerprint(), result));
        return new Execution(result, false);
    }
}
