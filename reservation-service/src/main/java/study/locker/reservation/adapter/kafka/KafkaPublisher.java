package study.locker.reservation.adapter.kafka;

import org.springframework.kafka.core.KafkaTemplate;

import study.locker.reservation.application.PublishOutbox;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public record KafkaPublisher(KafkaTemplate<String, String> kafka, Duration ackTimeout)
        implements PublishOutbox.Publisher {
    public PublishOutbox.Publication publish(PublishOutbox.Message event) {
        try {
            kafka.send(event.topic(), event.reservation().toString(), event.payload())
                    .get(ackTimeout.toNanos(), TimeUnit.NANOSECONDS);
            return PublishOutbox.Publication.ACKNOWLEDGED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return PublishOutbox.Publication.INTERRUPTED;
        } catch (Exception failure) {
            return PublishOutbox.Publication.FAILED;
        }
    }
}
