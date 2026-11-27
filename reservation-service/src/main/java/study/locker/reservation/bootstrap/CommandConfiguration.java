package study.locker.reservation.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.adapter.observability.*;
import study.locker.reservation.adapter.transaction.*;
import study.locker.reservation.application.*;
import study.locker.common.PickupTransit;
import study.locker.reservation.adapter.crypto.TransitCommandFingerprints;

@Configuration(proxyBeanMethods = false)
class CommandConfiguration {
    @Bean
    CommandHandler commands(CommandJournal journal, PickupTransit transit, JdbcClient db, TransactionTemplate transactions) {
        var replay = new IdempotentCommands(journal, new TransitCommandFingerprints(transit));
        var commit = new TransactionalCommands(transactions, new TraceCommandTransaction(db, replay));
        return new ObservedCommands(commit);
    }
}
