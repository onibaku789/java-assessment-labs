package study.locker.receipt.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;

import software.amazon.awssdk.services.s3.S3Client;

import study.locker.delivery.EventDecoder;
import study.locker.receipt.ReceiptApp;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Testcontainers
@SpringBootTest(classes = {ReceiptApp.class,
        ReceiptInfrastructure.Storage.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest", "spring.kafka.listener.ack-mode=record",
        "locker.delivery.retry.delay=125ms", "locker.delivery.retry.retries=2", "locker.delivery.retry.probe=500ms",
        "spring.kafka.listener.poll-timeout=100ms"})
class ReceiptDeliveryIT extends ReceiptInfrastructure {
    record LifecycleEvent(int version, UUID eventId, UUID reservationId, String compartmentId, String type,
                          String occurredAt) {
    }

    @Autowired
    KafkaTemplate<String, String> producer;
    @Autowired
    S3Client objects;
    @Autowired
    ObjectMapper json;

    @Autowired
    MeterRegistry metrics;
    @MockitoSpyBean
    EventDecoder decoder;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @Test
    void unavailableStorageKeepsTheOffsetAcrossAConsumerRestart() throws Exception {
        UUID booking = UUID.randomUUID();
        var event = new LifecycleEvent(1, UUID.randomUUID(), booking, "NORTH-L1", "RESERVED", "2026-01-01T00:00:00Z");
        var docker = store.getDockerClient();
        String topic = "locker.lifecycle.v1";
        docker.pauseContainerCmd(store.getContainerId()).exec();
        try {
            producer.send(topic, booking.toString(), json.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(35))
                    .untilAsserted(() -> assertThat(deliveries(topic, "waiting_for_dependency")).isPositive());

            double failuresBeforeRestart = deliveries(topic, "failed");
            listeners.stop();
            listeners.start();
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(deliveries(topic, "failed")).isGreaterThan(failuresBeforeRestart));
        } finally {
            docker.unpauseContainerCmd(store.getContainerId()).exec();
        }
        String prefix = "receipts/" + booking + "/";
        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertThat(receiptKeys(prefix)).containsExactly(prefix + event.eventId() + ".json"));
    }

    private List<String> receiptKeys(String prefix) {
        var listing = objects.listObjectsV2(request -> request.bucket("locker-audit").prefix(prefix));
        return listing.contents().stream().map(object -> object.key()).toList();
    }

    private double deliveries(String topic, String outcome) {
        var counter = metrics.find("locker.worker.deliveries").tags("topic", topic, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void duplicateReceiptsConvergeAndPoisonRecordsGoToDeadLettersWithoutBlockingLaterEvents() throws Exception {
        String topic = "locker.lifecycle.v1";
        UUID reservation = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        var saved = new LifecycleEvent(1, original, reservation, "CENTRAL-S1", "RESERVED",
                "2026-01-01T01:00:00.000+01:00");
        var conflict = new LifecycleEvent(1, original, reservation, "CENTRAL-S1", "CANCELLED", saved.occurredAt());
        var next = new LifecycleEvent(1, UUID.randomUUID(), reservation, "CENTRAL-S1", "CANCELLED", saved.occurredAt());
        String invalid = json.writeValueAsString(
                new LifecycleEvent(1, UUID.randomUUID(), reservation, " ", "RESERVED", "yesterday"));
        String wrongVersion = json.writeValueAsString(
                new LifecycleEvent(2, original, reservation, "CENTRAL-S1", "RESERVED", saved.occurredAt()));
        Map<String, String> badFields = Map.of(invalid, "compartmentId, occurredAt", wrongVersion, "version",
                "invalid-json", "payload", "{}", "compartmentId, eventId, occurredAt, reservationId, type, version");
        String prefix = "receipts/" + reservation + "/";
        try (var dlt = deadLetters()) {
            dlt.subscribe(List.of(topic + ".dlt"));
            for (String body : List.of(json.writeValueAsString(saved), json.writeValueAsString(saved),
                    json.writeValueAsString(conflict), "invalid-json", invalid, wrongVersion, "{}",
                    json.writeValueAsString(next))) {
                producer.send(topic, reservation.toString(), body).get(15, TimeUnit.SECONDS);
            }
            List<String> dead = new ArrayList<>();
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                dlt.poll(Duration.ofMillis(300)).forEach(record -> {
                    String fields = badFields.get(record.value());
                    if (fields != null) {
                        var trace = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE);
                        assertThat(new String(trace.value(), StandardCharsets.UTF_8)).contains(
                                "Invalid event fields: " + fields).doesNotContain("yesterday");
                    }
                    dead.add(record.value());
                });
                assertThat(dead).containsExactly(json.writeValueAsString(conflict), "invalid-json", invalid,
                        wrongVersion, "{}");
                assertThat(receiptKeys(prefix)).containsExactlyInAnyOrder(prefix + original + ".json",
                        prefix + next.eventId() + ".json");
            });
            assertThat(json.readValue(
                    objects.getObjectAsBytes(r -> r.bucket("locker-audit").key(prefix + original + ".json"))
                            .asByteArray(), LifecycleEvent.class)).isEqualTo(saved);
            verify(decoder, times(1)).decode(eq(invalid), any());
            verify(decoder, times(1)).decode(eq("invalid-json"), any());
            for (String outcome : List.of("replayed", "dead_lettered", "stored")) {
                assertThat(deliveries(topic, outcome)).as(outcome).isGreaterThanOrEqualTo(1);
            }
        }
    }
}
