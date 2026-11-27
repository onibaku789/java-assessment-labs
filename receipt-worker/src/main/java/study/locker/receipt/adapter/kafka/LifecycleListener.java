package study.locker.receipt.adapter.kafka;

import jakarta.validation.Valid;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import study.locker.delivery.DeliveryHandler;
import study.locker.receipt.domain.model.Receipt;

@Component
class LifecycleListener {
    private static final String TOPIC = "locker.lifecycle.v1";
    private final DeliveryHandler<Receipt> handler;

    LifecycleListener(DeliveryHandler<Receipt> handler) {
        this.handler = handler;
    }

    @KafkaListener(
            containerPostProcessor = "deliveryAttempts",
            errorHandler = "eventFieldErrors",
            topics = TOPIC,
            groupId = "locker-receipts-v1")
    void receive(
            @Payload @Valid LifecycleMessage payload,
            @Header(KafkaHeaders.DELIVERY_ATTEMPT) int attempt)
            throws Exception {
        handler.deliver(TOPIC, payload.toReceipt(), attempt);
    }
}
