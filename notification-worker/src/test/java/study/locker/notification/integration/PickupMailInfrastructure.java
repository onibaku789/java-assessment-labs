package study.locker.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

abstract class PickupMailInfrastructure {
    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    static final GenericContainer<?> inbox = new GenericContainer<>("axllent/mailpit:v1.27.4").withExposedPorts(1025,
            8025).waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    static final String TOPIC = "locker.pickup-mail.v1";
    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    @TempDir
    static Path config;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) throws Exception {
        Path secret = Files.writeString(config.resolve("pickup.key"), KEY);
        properties.add("locker.secret", secret::toString);
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        properties.add("spring.mail.host", inbox::getHost);
        properties.add("spring.mail.port", () -> inbox.getMappedPort(1025));
    }

    String inboxMessages() throws Exception {
        var uri = URI.create("http://" + inbox.getHost() + ":" + inbox.getMappedPort(8025) + "/api/v1/messages");
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }
    }

    static KafkaConsumer<String, String> deadLetters() {
        return new KafkaConsumer<String, String>(
                Map.of("bootstrap.servers", kafka.getBootstrapServers(), "group.id", "inspect-mail-dlt",
                        "auto.offset.reset", "earliest"), new StringDeserializer(), new StringDeserializer());
    }
}
