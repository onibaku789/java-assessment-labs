package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.adapter.kafka.KafkaPublisher;
import study.locker.reservation.adapter.transaction.TransactionalOutbox;
import study.locker.reservation.application.*;

import java.time.*;
import java.util.*;

class OutboxRelayIT extends ReservationFixture {
    @Autowired
    PublishOutbox.Queue queue;
    @Autowired
    TransactionTemplate tx;


    @Test
    void onlyOneRelayCanClaimTheOutboxUntilItsTransactionEnds() {
        tx.executeWithoutResult(holder -> {
            assertThat(queue.claimBatch()).isTrue();
            ReservationTestDatabase.independentTransaction(tx).executeWithoutResult(contender -> {
                db.sql("set local lock_timeout = '500ms'").update();
                assertThat(queue.claimBatch()).isFalse();
            });
            holder.setRollbackOnly();
        });
        tx.executeWithoutResult(successor -> assertThat(queue.claimBatch()).isTrue());
    }

    @Test
    void relayPublishesTheOldestConfiguredNumberOfRowsAndLeavesTheRestForTheNextBatch() {
        db.sql("""
                insert into outbox (seq, event_id, reservation_id, topic, payload) overriding system value
                select n, gen_random_uuid(), gen_random_uuid(), 'locker.lifecycle.v1', '{}'
                from generate_series(51, 1, -1) n
                """).update();
        var sequences = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        var published = new ArrayList<Long>();
        var relay = new TransactionalOutbox(tx, new PublishOutbox(queue, 27, message -> {
            published.add(message.sequence());
            return PublishOutbox.Publication.ACKNOWLEDGED;
        }));

        relay.publish();
        assertThat(published).containsExactlyElementsOf(sequences.subList(0, 27));
        assertThat(queue.count()).isEqualTo(24);

        relay.publish();
        assertThat(published).containsExactlyElementsOf(sequences);
        assertThat(queue.count()).isZero();
    }

    @Test
    void outboxSurvivesBrokerFailureAndReplaysIdenticalEventsAfterLostAcknowledgement() throws Exception {
        var booking = reserve();
        String topic = "locker.lifecycle.v1";
        String payload = db.sql("select payload from outbox").query(String.class).single();
        relay("127.0.0.1:1", false);
        assertThat(db.sql("select payload from outbox").query(String.class).single()).isEqualTo(payload);
        try (var broker = new org.testcontainers.kafka.KafkaContainer("apache/kafka:4.2.2")) {
            broker.start();
            relay(broker.getBootstrapServers(), true);
            assertThat(db.sql("select payload from outbox").query(String.class).single()).isEqualTo(payload);
            relay(broker.getBootstrapServers(), false);
            assertThat(db.sql("select count(*) from outbox").query(Integer.class).single()).isZero();
            try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                    Map.of("bootstrap.servers", broker.getBootstrapServers(), "group.id", "outbox-proof",
                            "auto.offset.reset", "earliest"),
                    new org.apache.kafka.common.serialization.StringDeserializer(),
                    new org.apache.kafka.common.serialization.StringDeserializer())) {
                consumer.subscribe(List.of(topic));
                List<String> seen = new ArrayList<>();
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                    consumer.poll(Duration.ofMillis(350)).forEach(event -> {
                        assertThat(event.key()).isEqualTo(booking.getId());
                        seen.add(event.value());
                    });
                    assertThat(seen).containsExactly(payload, payload);
                });
            }
        }
    }

    private void relay(String bootstrap, boolean simulateLostCommit) {
        var producer = new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String>(
                Map.of("bootstrap.servers", bootstrap, "max.block.ms", bootstrap.endsWith(":1") ? 300 : 10000),
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.StringSerializer());
        try {
            var relay = new TransactionalOutbox(tx, new PublishOutbox(queue, 50,
                    new KafkaPublisher(new org.springframework.kafka.core.KafkaTemplate<>(producer),
                            Duration.ofSeconds(12))));
            tx.executeWithoutResult(txStatus -> {
                relay.publish();
                if (simulateLostCommit) txStatus.setRollbackOnly();
            });
        } finally {
            producer.destroy();
        }
    }
}
