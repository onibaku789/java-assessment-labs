package study.locker.reservation.bootstrap;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.adapter.kafka.KafkaPublisher;
import study.locker.reservation.adapter.observability.*;
import study.locker.reservation.adapter.transaction.*;
import study.locker.reservation.application.*;

@EnableConfigurationProperties(OutboxProperties.class)
@Configuration(proxyBeanMethods = false)
class OutboxConfiguration {
    @Bean
    OutboxRelay outbox(PublishOutbox.Queue queue, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       OutboxProperties settings) {
        return new TransactionalOutbox(tx, new PublishOutbox(queue, settings.batchSize(),
                new ObservedPublisher(new KafkaPublisher(kafka, settings.ackTimeout()))));
    }

    @Bean
    @ConditionalOnProperty(name = "locker.outbox.scheduling", matchIfMissing = true)
    RelaySchedule relaySchedule(OutboxRelay outbox) {
        return new RelaySchedule(outbox);
    }

    @Bean
    MeterBinder outboxBacklog(PublishOutbox.Queue queue) {
        return registry -> Gauge.builder("locker.outbox.pending", queue, PublishOutbox.Queue::count).register(registry);
    }

    record RelaySchedule(OutboxRelay outbox) {
        @Scheduled(fixedDelayString = "${locker.outbox.poll-interval}")
        public void publish() {
            outbox.publish();
        }
    }
}
