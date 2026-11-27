package study.locker.receipt.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import study.locker.delivery.EventDecoder;
import study.locker.receipt.ReceiptApp;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Testcontainers
@SpringBootTest(
        classes = {ReceiptApp.class, ReceiptDeliveryIT.Storage.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.listener.ack-mode=record",
            "locker.delivery.retry.delay=125ms",
            "locker.delivery.retry.retries=2"
        })
class ReceiptDeliveryIT {
    record LifecycleEvent(
            int version,
            UUID eventId,
            UUID reservationId,
            String compartmentId,
            String type,
            String occurredAt) {}

    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    static final GenericContainer<?> store =
            new GenericContainer<>("adobe/s3mock:5.2.3")
                    .withExposedPorts(9090)
                    .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "locker-audit")
                    .waitingFor(Wait.forHttp("/locker-audit").forPort(9090).forStatusCode(200));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Storage {
        @Bean
        @Primary
        S3Client testStorage() {
            return S3Client.builder()
                    .endpointOverride(
                            URI.create(
                                    "http://" + store.getHost() + ":" + store.getMappedPort(9090)))
                    .region(Region.US_EAST_1)
                    .forcePathStyle(true)
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("lab", "lab")))
                    .build();
        }
    }

    @Autowired KafkaTemplate<String, String> producer;
    @Autowired S3Client objects;
    @Autowired ObjectMapper json;

    @Autowired MeterRegistry metrics;
    @MockitoSpyBean EventDecoder decoder;

    @Test
    void duplicateReceiptsConvergeAndPoisonRecordsGoToDeadLettersWithoutBlockingLaterEvents()
            throws Exception {
        String topic = "locker.lifecycle.v1";
        UUID reservation = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        var saved =
                new LifecycleEvent(
                        1,
                        original,
                        reservation,
                        "CENTRAL-S1",
                        "RESERVED",
                        "2026-01-01T01:00:00.000+01:00");
        var conflict =
                new LifecycleEvent(
                        1, original, reservation, "CENTRAL-S1", "CANCELLED", saved.occurredAt());
        var next =
                new LifecycleEvent(
                        1,
                        UUID.randomUUID(),
                        reservation,
                        "CENTRAL-S1",
                        "CANCELLED",
                        saved.occurredAt());
        String invalid =
                json.writeValueAsString(
                        new LifecycleEvent(
                                1, UUID.randomUUID(), reservation, " ", "RESERVED", "yesterday"));
        String wrongVersion =
                json.writeValueAsString(
                        new LifecycleEvent(
                                2,
                                original,
                                reservation,
                                "CENTRAL-S1",
                                "RESERVED",
                                saved.occurredAt()));
        Map<String, String> badFields =
                Map.of(
                        invalid,
                        "compartmentId, occurredAt",
                        wrongVersion,
                        "version",
                        "{}",
                        "compartmentId, eventId, occurredAt, reservationId, type, version");
        String prefix = "receipts/" + reservation + "/";
        try (var dlt =
                new KafkaConsumer<String, String>(
                        Map.of(
                                "bootstrap.servers",
                                kafka.getBootstrapServers(),
                                "group.id",
                                "inspect-dead-letters",
                                "auto.offset.reset",
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer())) {
            dlt.subscribe(List.of(topic + ".dlt"));
            for (String body :
                    List.of(
                            json.writeValueAsString(saved),
                            json.writeValueAsString(saved),
                            json.writeValueAsString(conflict),
                            "invalid-json",
                            invalid,
                            wrongVersion,
                            "{}",
                            json.writeValueAsString(next))) {
                producer.send(topic, reservation.toString(), body).get(15, TimeUnit.SECONDS);
            }
            List<String> dead = new ArrayList<>();
            await().atMost(Duration.ofSeconds(45))
                    .untilAsserted(
                            () -> {
                                dlt.poll(Duration.ofMillis(300))
                                        .forEach(
                                                record -> {
                                                    String fields = badFields.get(record.value());
                                                    if (fields != null) {
                                                        var trace =
                                                                record.headers()
                                                                        .lastHeader(
                                                                                KafkaHeaders
                                                                                        .DLT_EXCEPTION_STACKTRACE);
                                                        assertThat(
                                                                        new String(
                                                                                trace.value(),
                                                                                StandardCharsets
                                                                                        .UTF_8))
                                                                .contains(
                                                                        "Invalid event fields: "
                                                                                + fields)
                                                                .doesNotContain("yesterday");
                                                    }
                                                    dead.add(record.value());
                                                });
                                assertThat(dead)
                                        .containsExactly(
                                                json.writeValueAsString(conflict),
                                                "invalid-json",
                                                invalid,
                                                wrongVersion,
                                                "{}");
                                assertThat(
                                                objects.listObjectsV2(
                                                                r ->
                                                                        r.bucket("locker-audit")
                                                                                .prefix(prefix))
                                                        .contents())
                                        .extracting(object -> object.key())
                                        .containsExactlyInAnyOrder(
                                                prefix + original + ".json",
                                                prefix + next.eventId() + ".json");
                            });
            assertThat(
                            json.readValue(
                                    objects.getObjectAsBytes(
                                                    r ->
                                                            r.bucket("locker-audit")
                                                                    .key(
                                                                            prefix + original
                                                                                    + ".json"))
                                            .asByteArray(),
                                    LifecycleEvent.class))
                    .isEqualTo(saved);
            verify(decoder, times(1)).decode(eq(invalid), any());
            verify(decoder, times(3)).decode(eq("invalid-json"), any());
            for (String outcome :
                    List.of(
                            "replayed",
                            "failed",
                            "retry_failed",
                            "retried",
                            "dead_lettered",
                            "stored")) {
                assertThat(
                                metrics.get("locker.worker.deliveries")
                                        .tags("topic", topic, "outcome", outcome)
                                        .counter()
                                        .count())
                        .as(outcome)
                        .isGreaterThanOrEqualTo(1);
            }
        }
    }
}
