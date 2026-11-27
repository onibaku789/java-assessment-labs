package study.locker.receipt.integration;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;
import java.util.*;

abstract class ReceiptInfrastructure {
    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    static final GenericContainer<?> store = new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(9090)
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
                    .endpointOverride(URI.create("http://" + store.getHost() + ":" + store.getMappedPort(9090)))
                    .region(Region.US_EAST_1).overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(1))
                            .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("lab", "lab")))
                    .build();
        }
    }

    static KafkaConsumer<String, String> deadLetters() {
        return new KafkaConsumer<String, String>(
                Map.of("bootstrap.servers", kafka.getBootstrapServers(), "group.id", "inspect-dead-letters",
                        "auto.offset.reset", "earliest"), new StringDeserializer(), new StringDeserializer());
    }
}
