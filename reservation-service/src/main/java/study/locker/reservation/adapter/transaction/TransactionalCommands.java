package study.locker.reservation.adapter.transaction;

import org.springframework.transaction.support.TransactionTemplate;

import study.locker.reservation.application.Caller;
import study.locker.reservation.application.CommandHandler;

public record TransactionalCommands(TransactionTemplate transactions, CommandHandler delegate)
        implements CommandHandler {
    @Override
    public Execution execute(Caller caller, Command command) {
        return transactions.execute(status -> delegate.execute(caller, command));
    }
}
