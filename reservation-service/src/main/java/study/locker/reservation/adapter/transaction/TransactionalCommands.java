package study.locker.reservation.adapter.transaction;

import org.springframework.transaction.support.TransactionTemplate;

import study.locker.reservation.application.CommandHandler;

import java.util.function.Supplier;

public record TransactionalCommands(TransactionTemplate transactions, CommandHandler delegate)
        implements CommandHandler {
    @Override
    public Execution execute(Command command, Supplier<Result> work) {
        return transactions.execute(status -> delegate.execute(command, work));
    }
}
