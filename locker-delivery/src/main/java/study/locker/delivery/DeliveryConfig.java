package study.locker.delivery;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.ContainerPostProcessor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import tools.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;

@EnableConfigurationProperties(DeliveryRetryProperties.class)
@Configuration(proxyBeanMethods = false)
public class DeliveryConfig {
    @Bean
    ContainerPostProcessor<String, String, AbstractMessageListenerContainer<String, String>>
            deliveryAttempts() {
        return container -> container.getContainerProperties().setDeliveryAttemptHeader(true);
    }

    @Bean
    EventDecoder eventDecoder(ObjectMapper json) {
        return new EventDecoder(json);
    }

    @Bean
    KafkaListenerConfigurer listenerValidation(LocalValidatorFactoryBean validator) {
        return registrar -> registrar.setValidator(validator);
    }

    @Bean
    KafkaListenerErrorHandler eventFieldErrors() {
        return (message, failure) -> {
            if (failure.getCause() instanceof MethodArgumentNotValidException invalid
                    && invalid.getBindingResult() != null) {
                String fields =
                        invalid.getBindingResult().getFieldErrors().stream()
                                .map(FieldError::getField)
                                .distinct()
                                .sorted()
                                .collect(Collectors.joining(", "));
                throw new InvalidEventFields(fields);
            }
            throw failure;
        };
    }

    @Bean
    DeliveryEvents deliveryEvents(MeterRegistry metrics) {
        return new DeliveryEvents(metrics);
    }

    @Bean
    DefaultErrorHandler consumerErrorHandler(
            KafkaTemplate<String, String> producer,
            DeliveryEvents events,
            ObjectMapper json,
            DeliveryRetryProperties retry) {
        var deadLetters =
                new DeadLetterPublishingRecoverer(
                        producer,
                        (record, failure) ->
                                new TopicPartition(record.topic() + ".dlt", record.partition()));
        deadLetters.setFailIfSendResultIsError(true);
        var handler =
                new DefaultErrorHandler(
                        deadLetters, new FixedBackOff(retry.delay().toMillis(), retry.retries()));
        handler.addNotRetryableExceptions(InvalidEventFields.class);
        handler.setRetryListeners(
                new RetryListener() {
                    private void report(ConsumerRecord<?, ?> record, String outcome) {
                        String event = null;
                        try {
                            event =
                                    json.readTree(String.valueOf(record.value()))
                                            .path("eventId")
                                            .asString(null);
                        } catch (RuntimeException invalidPayload) {
                            /* A malformed record still has an offset and key. */
                        }
                        events.record(record.topic(), outcome, String.valueOf(record.key()), event);
                    }

                    public void failedDelivery(
                            ConsumerRecord<?, ?> record, Exception failure, int attempt) {
                        report(record, attempt == 1 ? "failed" : "retry_failed");
                    }

                    public void recovered(ConsumerRecord<?, ?> record, Exception failure) {
                        report(record, "dead_lettered");
                    }

                    public void recoveryFailed(
                            ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                        report(record, "dead_letter_failed");
                    }
                });
        return handler;
    }
}
