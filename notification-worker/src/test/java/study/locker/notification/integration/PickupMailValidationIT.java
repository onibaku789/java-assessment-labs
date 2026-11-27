package study.locker.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

import study.locker.common.PickupSecrets;
import study.locker.delivery.EventDecoder;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
class PickupMailValidationIT {
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    static final GenericContainer<?> inbox =
            new GenericContainer<>("axllent/mailpit:v1.27.4")
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    private static final String TOPIC = "locker.pickup-mail.v1";
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    @TempDir static Path config;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) throws Exception {
        Path secret = Files.writeString(config.resolve("pickup.key"), KEY);
        properties.add("locker.secret", secret::toString);
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        properties.add("spring.mail.host", inbox::getHost);
        properties.add("spring.mail.port", () -> inbox.getMappedPort(1025));
    }

    @Autowired KafkaTemplate<String, String> producer;
    @Autowired ObjectMapper json;
    @Autowired MeterRegistry metrics;
    @MockitoSpyBean EventDecoder decoder;

    @Test
    void invalidEventGoesStraightToDeadLettersAndOnlyTheValidMessageSendsMail() throws Exception {
        String reservation = UUID.randomUUID().toString();
        String invalid =
                message(
                        "not-a-uuid",
                        reservation,
                        "not-an-address",
                        Instant.now().plusSeconds(600),
                        "private-test-envelope");
        String invalidFields =
                json.writeValueAsString(
                        Map.of(
                                "version",
                                2,
                                "eventId",
                                "bad-event",
                                "reservationId",
                                "0-0-0-0-1",
                                "recipient",
                                "private-invalid-recipient",
                                "expiresAt",
                                "not-an-instant",
                                "envelope",
                                " "));
        String allFields = "envelope, eventId, expiresAt, recipient, reservationId, version";
        Map<String, String> expectedFields =
                Map.of(invalid, "eventId, recipient", invalidFields, allFields, "{}", allFields);
        String expired =
                message(
                        UUID.randomUUID().toString(),
                        reservation,
                        "test@example.test",
                        Instant.EPOCH,
                        "expired-envelope");
        // GCM authenticates the original event ID text, including its case.
        String eventId = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        String valid =
                message(
                        eventId,
                        reservation,
                        "test@example.test",
                        Instant.now().plusSeconds(600),
                        new PickupSecrets(KEY).seal(eventId, "01234567"));

        try (var dlt =
                new KafkaConsumer<String, String>(
                        Map.of(
                                "bootstrap.servers",
                                kafka.getBootstrapServers(),
                                "group.id",
                                "inspect-mail-dlt",
                                "auto.offset.reset",
                                "earliest"),
                        new StringDeserializer(),
                        new StringDeserializer())) {
            dlt.subscribe(List.of(TOPIC + ".dlt"));
            for (String message : List.of(invalid, invalidFields, "{}", expired, valid))
                producer.send(TOPIC, reservation, message).get(15, TimeUnit.SECONDS);

            var dead = new ArrayList<String>();
            await().atMost(Duration.ofSeconds(45))
                    .untilAsserted(
                            () -> {
                                dlt.poll(Duration.ofMillis(200))
                                        .forEach(
                                                record -> {
                                                    assertThat(record.key()).isEqualTo(reservation);
                                                    var trace =
                                                            record.headers()
                                                                    .lastHeader(
                                                                            KafkaHeaders
                                                                                    .DLT_EXCEPTION_STACKTRACE);
                                                    assertThat(
                                                                    new String(
                                                                            trace.value(),
                                                                            StandardCharsets.UTF_8))
                                                            .contains(
                                                                    "Invalid event fields: "
                                                                            + expectedFields.get(
                                                                                    record.value()))
                                                            .doesNotContain(
                                                                    "private-invalid-recipient",
                                                                    "private-test-envelope",
                                                                    "not-an-address");
                                                    dead.add(record.value());
                                                });
                                assertThat(dead).containsExactly(invalid, invalidFields, "{}");
                                var sent =
                                        metrics.find("locker.worker.deliveries")
                                                .tags("topic", TOPIC, "outcome", "sent")
                                                .counter();
                                assertThat(sent).isNotNull();
                                assertThat(sent.count()).isEqualTo(1);
                            });
        }
        assertThat(
                        metrics.get("locker.worker.deliveries")
                                .tags("topic", TOPIC, "outcome", "expired")
                                .counter()
                                .count())
                .isEqualTo(1);
        verify(decoder, times(1)).decode(eq(invalid), any());
        var uri =
                URI.create(
                        "http://"
                                + inbox.getHost()
                                + ":"
                                + inbox.getMappedPort(8025)
                                + "/api/v1/messages");
        try (var http = HttpClient.newHttpClient()) {
            var response =
                    http.send(
                            HttpRequest.newBuilder(uri).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var messages = json.readTree(response.body()).path("messages");
            assertThat(messages.size()).isOne();
            assertThat(messages.get(0).path("Subject").asString())
                    .isEqualTo("Pickup ready for " + reservation);
        }
    }

    private String message(
            String id, String reservation, String recipient, Instant expires, String envelope) {
        return json.writeValueAsString(
                Map.of(
                        "version",
                        1,
                        "eventId",
                        id,
                        "reservationId",
                        reservation,
                        "recipient",
                        recipient,
                        "expiresAt",
                        expires.toString(),
                        "envelope",
                        envelope));
    }
}
