package study.locker.receipt.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

import study.locker.delivery.*;
import study.locker.receipt.adapter.s3.S3ReceiptArchive;
import study.locker.receipt.application.StoreLifecycleReceipt;
import study.locker.receipt.domain.model.Receipt;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
@Import(DeliveryConfig.class)
class ReceiptConfiguration {
    @Bean(destroyMethod = "close")
    S3Client storage(
            @Value("${locker.storage:http://objects:9090}") URI address,
            @Value("${aws.region:us-east-1}") String region,
            @Value("${locker.receipts.timeout:5s}") Duration timeout) {
        return S3Client.builder()
                .endpointOverride(address)
                .region(Region.of(region))
                .overrideConfiguration(
                        c ->
                                c.apiCallTimeout(timeout)
                                        .retryStrategy(
                                                StandardRetryStrategy.builder()
                                                        .maxAttempts(1)
                                                        .build()))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    StoreLifecycleReceipt receipts(
            S3Client s3,
            ObjectMapper json,
            @Value("${locker.receipts.bucket:locker-audit}") String bucket) {
        return new StoreLifecycleReceipt(new S3ReceiptArchive(s3, json, bucket));
    }

    @Bean
    DeliveryHandler<Receipt> handler(DeliveryEvents events, StoreLifecycleReceipt useCase) {
        return new ObservedDelivery<>(
                events,
                msg ->
                        new ObservedDelivery.Correlation(
                                Objects.toString(msg.reservationId(), null),
                                Objects.toString(msg.eventId(), null)),
                useCase::store);
    }
}
