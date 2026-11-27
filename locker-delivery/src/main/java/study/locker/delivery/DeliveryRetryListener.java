package study.locker.delivery;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

record DeliveryRetryListener(DeliveryEvents events, ObjectMapper json, int shortRetries)
        implements RetryListener {
    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception failure, int attempt) {
        String eventId = eventId(record);
        report(record, attempt == 1 ? "failed" : "retry_failed", eventId);
        if (attempt > shortRetries && DeliveryUnavailable.causedBy(failure)) {
            report(record, "waiting_for_dependency", eventId);
        }
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception failure) {
        report(record, "dead_lettered", eventId(record));
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        report(record, "dead_letter_failed", eventId(record));
    }

    private void report(ConsumerRecord<?, ?> record, String outcome, String eventId) {
        events.record(record.topic(), outcome, String.valueOf(record.key()), eventId);
    }

    private String eventId(ConsumerRecord<?, ?> record) {
        if (!(record.value() instanceof String payload)) {
            return null;
        }
        try {
            var event = json.readTree(payload);
            return event == null ? null : event.path("eventId").asString(null);
        } catch (JacksonException malformed) {
            // A malformed record can still be identified by its Kafka metadata.
            return null;
        }
    }
}
