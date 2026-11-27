package study.locker.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;

import study.locker.delivery.EventDecoder;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest", "spring.kafka.listener.poll-timeout=100ms",
        "locker.delivery.retry.retries=0", "locker.delivery.retry.probe=500ms",
        "spring.mail.properties.mail.smtp.timeout=500", "spring.mail.properties.mail.smtp.connectiontimeout=500"})
class PickupMailValidationIT extends PickupMailInfrastructure {
    @MockitoBean
    Clock clock;

    @BeforeEach
    void realTime() {
        when(clock.instant()).thenAnswer(inv -> Instant.now());
    }

    private double count(String outcome) {
        var counter = metrics.find("locker.worker.deliveries").tags("topic", TOPIC, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void parkedMailChecksExpiryAgainBeforeSending() throws Exception {
        String reservation = UUID.randomUUID().toString();
        String event = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(60);
        double expiredBefore = count("expired");
        double parkedBefore = count("waiting_for_dependency");
        var docker = inbox.getDockerClient();
        docker.pauseContainerCmd(inbox.getContainerId()).exec();
        try {
            producer.send(TOPIC, reservation, message(event, reservation, "test@example.test", expires,
                    vault.transit("reservations").seal(event, "01234567"))).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).until(() -> count("waiting_for_dependency") > parkedBefore);
            when(clock.instant()).thenReturn(expires);
        } finally {
            docker.unpauseContainerCmd(inbox.getContainerId()).exec();
        }
        await().atMost(Duration.ofSeconds(20)).until(() -> count("expired") > expiredBefore);
        assertThat(inboxMessages()).doesNotContain(reservation);
    }

    @Test
    void pendingMailResumesWhenVaultRecovers() throws Exception {
        String reservation = UUID.randomUUID().toString();
        String event = UUID.randomUUID().toString();
        String envelope = vault.transit("reservations").seal(event, "01234567");
        double parked = count("waiting_for_dependency");
        var docker = vault.getDockerClient();
        docker.pauseContainerCmd(vault.getContainerId()).exec();
        try {
            producer.send(TOPIC, reservation, message(event, reservation, "test@example.test",
                    Instant.now().plusSeconds(120), envelope)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(15)).until(() -> count("waiting_for_dependency") > parked);
            assertThat(inboxMessages()).doesNotContain(reservation);
        } finally {
            docker.unpauseContainerCmd(vault.getContainerId()).exec();
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(inboxMessages()).contains(reservation));
    }

    @Autowired
    KafkaTemplate<String, String> producer;
    @Autowired
    ObjectMapper json;
    @Autowired
    MeterRegistry metrics;
    @MockitoSpyBean
    EventDecoder decoder;

    @Test
    void invalidEventGoesStraightToDeadLettersAndOnlyTheValidMessageSendsMail() throws Exception {
        double expiredBefore = count("expired");
        double sentBefore = count("sent");
        String reservation = UUID.randomUUID().toString();
        String invalid = message("not-a-uuid", reservation, "not-an-address", Instant.now().plusSeconds(600),
                "private-test-envelope");
        String invalidFields = json.writeValueAsString(
                Map.of("version", 2, "eventId", "bad-event", "reservationId", "0-0-0-0-1", "recipient",
                        "private-invalid-recipient", "expiresAt", "not-an-instant", "envelope", " "));
        String allFields = "envelope, eventId, expiresAt, recipient, reservationId, version";
        Map<String, String> expectedFields = Map.of(invalid, "eventId, recipient", invalidFields, allFields, "{}",
                allFields);
        String expired = message(UUID.randomUUID().toString(), reservation, "test@example.test", Instant.EPOCH,
                "expired-envelope");
        // GCM authenticates the original event ID text, including its case.
        String eventId = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        String valid = message(eventId, reservation, "test@example.test", Instant.now().plusSeconds(600),
                vault.transit("reservations").seal(eventId, "01234567"));

        try (var dlt = deadLetters()) {
            dlt.subscribe(List.of(TOPIC + ".dlt"));
            for (String message : List.of(invalid, invalidFields, "{}", expired, valid))
                producer.send(TOPIC, reservation, message).get(15, TimeUnit.SECONDS);

            var dead = new ArrayList<String>();
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                dlt.poll(Duration.ofMillis(200)).forEach(record -> {
                    assertThat(record.key()).isEqualTo(reservation);
                    var trace = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE);
                    assertThat(new String(trace.value(), StandardCharsets.UTF_8)).contains(
                                    "Invalid event fields: " + expectedFields.get(record.value()))
                            .doesNotContain("private-invalid-recipient", "private-test-envelope", "not-an-address");
                    dead.add(record.value());
                });
                assertThat(dead).containsExactly(invalid, invalidFields, "{}");
                var sent = metrics.find("locker.worker.deliveries").tags("topic", TOPIC, "outcome", "sent").counter();
                assertThat(sent).isNotNull();
                assertThat(sent.count()).isEqualTo(sentBefore + 1);
            });
        }
        assertThat(metrics.get("locker.worker.deliveries").tags("topic", TOPIC, "outcome", "expired").counter()
                .count()).isEqualTo(expiredBefore + 1);
        verify(decoder, times(1)).decode(eq(invalid), any());
        var messages = json.readTree(inboxMessages()).path("messages");
        assertThat(messages.valueStream().map(item -> item.path("Subject").asString()))
                .filteredOn(("Pickup ready for " + reservation)::equals).hasSize(1);
    }

    private String message(String id, String reservation, String recipient, Instant expires, String envelope) {
        return json.writeValueAsString(
                Map.of("version", 1, "eventId", id, "reservationId", reservation, "recipient", recipient, "expiresAt",
                        expires.toString(), "envelope", envelope));
    }
}
