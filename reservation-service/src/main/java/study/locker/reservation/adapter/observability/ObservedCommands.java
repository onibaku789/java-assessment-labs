package study.locker.reservation.adapter.observability;

import study.locker.common.EventLog;
import study.locker.reservation.application.Caller;
import study.locker.reservation.application.CommandHandler;

public record ObservedCommands(CommandHandler delegate) implements CommandHandler {
    @Override
    public Execution execute(Caller caller, Command command) {
        var execution = delegate.execute(caller, command);
        var result = execution.result();
        String outcome = execution.replayed() ? "replayed" : result.error() == null ? "committed" : "rejected";
        EventLog.record("reservation_command", command.operation(), outcome, result.view().id().toString(),
                command.requestId().toString(), null);
        return execution;
    }
}
