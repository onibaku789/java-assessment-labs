package study.locker.delivery;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ContainerPostProcessor;
import org.springframework.kafka.listener.*;

@Import({DeliveryValidationConfiguration.class, DeliveryRecoveryConfiguration.class})
@EnableConfigurationProperties(DeliveryRetryProperties.class)
@Configuration(proxyBeanMethods = false)
public class DeliveryConfig {
    @Bean
    ContainerPostProcessor<String, String, AbstractMessageListenerContainer<String, String>> deliveryAttempts() {
        return container -> container.getContainerProperties().setDeliveryAttemptHeader(true);
    }

    @Bean
    DeliveryEvents deliveryEvents(MeterRegistry metrics) {
        return new DeliveryEvents(metrics);
    }

}
