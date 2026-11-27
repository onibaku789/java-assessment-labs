package study.locker.reservation.adapter.transaction;

import org.springframework.transaction.support.TransactionTemplate;

import study.locker.reservation.application.OutboxRelay;

public record TransactionalOutbox(TransactionTemplate transactions, OutboxRelay delegate)
        implements OutboxRelay {
    @Override
    public void publish() {
        transactions.executeWithoutResult(status -> delegate.publish());
    }
}
