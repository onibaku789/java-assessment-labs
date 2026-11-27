package study.locker.notification.adapter.kafka;

import jakarta.validation.Valid;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import study.locker.delivery.DeliveryHandler;
import study.locker.notification.domain.model.PickupNotification;

@Component
class PickupMailListener {
    private static final String TOPIC = "locker.pickup-mail.v1";
    private final DeliveryHandler<PickupNotification> handler;

    PickupMailListener(DeliveryHandler<PickupNotification> handler) {
        this.handler = handler;
    }

    @KafkaListener(
            containerPostProcessor = "deliveryAttempts",
            errorHandler = "eventFieldErrors",
            topics = TOPIC,
            groupId = "locker-notifications-v1")
    void receive(
            @Payload @Valid PickupMailMessage payload,
            @Header(KafkaHeaders.DELIVERY_ATTEMPT) int attempt)
            throws Exception {
        handler.deliver(TOPIC, payload.toNotification(), attempt);
    }
}
