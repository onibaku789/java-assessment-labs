package study.locker.delivery;

import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.listener.*;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
class DeliveryValidationConfiguration {
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
            if (failure.getCause() instanceof MethodArgumentNotValidException invalid &&
                    invalid.getBindingResult() != null) {
                String fields = invalid.getBindingResult().getFieldErrors().stream().map(FieldError::getField)
                        .distinct().sorted().collect(Collectors.joining(", "));
                throw new InvalidEventFields(fields);
            }
            throw failure;
        };
    }
}
