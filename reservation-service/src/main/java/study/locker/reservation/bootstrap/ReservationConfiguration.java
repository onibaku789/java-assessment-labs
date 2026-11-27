package study.locker.reservation.bootstrap;

import io.grpc.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;

import study.locker.common.*;
import study.locker.reservation.adapter.crypto.*;
import study.locker.reservation.adapter.grpcclient.GrpcDirectory;
import study.locker.reservation.adapter.grpcserver.ReservationEndpoint;
import study.locker.reservation.adapter.kafka.KafkaPublisher;
import study.locker.reservation.adapter.observability.*;
import study.locker.reservation.adapter.transaction.*;
import study.locker.reservation.application.*;
import study.locker.security.*;
import study.locker.wire.DirectoryGrpc;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PickupProperties.class, OutboxProperties.class})
@EnableMethodSecurity
@Import(LockerJwt.class)
class ReservationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PickupSecrets secrets(@Value("${locker.secret:/run/locker/pickup.key}") String path)
            throws IOException {
        return new PickupSecrets(Files.readString(Path.of(path)));
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel directoryChannel(@Value("${locker.directory:directory:9090}") String target) {
        return Rpc.channel(target);
    }

    @Bean
    Directory directory(
            ManagedChannel channel, @Value("${locker.directory.deadline}") Duration deadline) {
        return new GrpcDirectory(DirectoryGrpc.newBlockingStub(channel), deadline);
    }

    @Bean
    ReservationQueries queries(ReservationStore store, Directory directory) {
        return new ReservationQueries(store, directory);
    }

    @Bean
    CommandHandler commands(
            ReservationStore store,
            CommandJournal journal,
            JdbcClient db,
            Directory directory,
            ReservationEvents events,
            PickupSecrets secrets,
            PickupProperties pickup,
            TransactionTemplate transactions,
            Clock clock) {
        var business =
                new ReservationCommands(
                        store,
                        directory,
                        new SecurePickupCodes(secrets, pickup.ttl(), pickup.maxAttempts()),
                        events,
                        clock);
        var replay = new IdempotentCommands(journal, business);
        var commit =
                new TransactionalCommands(transactions, new TraceCommandTransaction(db, replay));
        return new ObservedCommands(commit);
    }

    @Bean(destroyMethod = "shutdownNow")
    Server reservationServer(
            ReservationAccess access,
            PickupSecrets secrets,
            JwtDecoder jwt,
            @Value("${locker.grpc.port:9090}") int port)
            throws IOException {
        return Rpc.serve(port, new ReservationEndpoint(access, secrets), new RpcIdentity(jwt));
    }

    @Bean
    OutboxRelay outbox(
            PublishOutbox.Queue queue,
            KafkaTemplate<String, String> kafka,
            TransactionTemplate tx,
            OutboxProperties settings) {
        return new TransactionalOutbox(
                tx,
                new PublishOutbox(
                        queue,
                        settings.batchSize(),
                        new ObservedPublisher(new KafkaPublisher(kafka, settings.ackTimeout()))));
    }

    @Bean
    @ConditionalOnProperty(name = "locker.outbox.scheduling", matchIfMissing = true)
    RelaySchedule relaySchedule(OutboxRelay outbox) {
        return new RelaySchedule(outbox);
    }

    record RelaySchedule(OutboxRelay outbox) {
        @Scheduled(fixedDelayString = "${locker.outbox.poll-interval}")
        public void publish() {
            outbox.publish();
        }
    }

    @Bean
    MeterBinder outboxBacklog(PublishOutbox.Queue queue) {
        return registry ->
                Gauge.builder("locker.outbox.pending", queue, PublishOutbox.Queue::count)
                        .register(registry);
    }
}
