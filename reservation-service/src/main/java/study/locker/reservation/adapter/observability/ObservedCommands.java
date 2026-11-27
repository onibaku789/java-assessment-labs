package study.locker.reservation.adapter.observability;

import study.locker.common.EventLog;
import study.locker.reservation.application.CommandHandler;

import java.util.function.Supplier;

public record ObservedCommands(CommandHandler delegate) implements CommandHandler {
    @Override
    public Execution execute(Command command, Supplier<Result> work) {
        var execution = delegate.execute(command, work);
        var result = execution.result();
        String outcome = execution.replayed() ? "replayed" : result.error() == null ? "committed" : "rejected";
        EventLog.record("reservation_command", command.operation(), outcome, result.view().id().toString(),
                command.requestId().toString(), null);
        return execution;
    }
}
