package study.locker.reservation.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.adapter.observability.*;
import study.locker.reservation.adapter.transaction.*;
import study.locker.reservation.application.*;

@Configuration(proxyBeanMethods = false)
class CommandConfiguration {
    @Bean
    CommandHandler commands(ReservationCommands business, CommandJournal journal, JdbcClient db,
                            TransactionTemplate transactions) {
        var replay = new IdempotentCommands(journal, business);
        var commit = new TransactionalCommands(transactions, new TraceCommandTransaction(db, replay));
        return new ObservedCommands(commit);
    }
}
