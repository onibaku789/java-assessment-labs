package study.locker.delivery;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class DeliveryRecoveryConfiguration {
    @Bean
    ThreadPoolTaskScheduler deliveryScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("delivery-resume-");
        return scheduler;
    }

    @Bean
    DefaultErrorHandler consumerErrorHandler(KafkaTemplate<String, String> producer, DeliveryEvents events,
                                             ObjectMapper json, DeliveryRetryProperties retry,
                                             KafkaListenerEndpointRegistry registry,
                                             ThreadPoolTaskScheduler deliveryScheduler) {
        var deadLetters = new DeadLetterPublishingRecoverer(producer,
                (record, failure) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        deadLetters.setFailIfSendResultIsError(true);
        var permanent = new FixedBackOff(0, 0);
        var pausing = new ContainerPausingBackOffHandler(
                new ListenerContainerPauseService(registry, deliveryScheduler));
        var handler = new DefaultErrorHandler(deadLetters, permanent, pausing);
        handler.setBackOffFunction(
                (record, failure) -> DeliveryUnavailable.causedBy(failure) ? retry.outage() : permanent);
        handler.addNotRetryableExceptions(InvalidEventFields.class);
        handler.setRetryListeners(new DeliveryRetryListener(events, json, retry.retries()));
        return handler;
    }

}
