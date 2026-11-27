package study.locker.reservation.application;

import study.locker.reservation.domain.model.ReservationFailure;

import java.util.function.Supplier;

public record IdempotentCommands(CommandJournal journal) implements CommandHandler {
    @Override
    public Execution execute(Command command, Supplier<Result> work) {
        var subject = command.subject();
        var key = command.requestId();
        journal.lock(subject, key);
        var saved = journal.find(subject, key);
        if (saved.isPresent()) return replay(command, saved.get());

        var result = work.get();
        journal.save(subject, key, new CommandJournal.Entry(command.fingerprint(), result));
        return new Execution(result, false);
    }

    private static Execution replay(Command command, CommandJournal.Entry entry) {
        if (!entry.fingerprint().equals(command.fingerprint())) {
            throw new ReservationFailure(ReservationFailure.Reason.REUSED_KEY,
                    "Request ID already used for different input");
        }
        return new Execution(entry.result(), true);
    }
}
