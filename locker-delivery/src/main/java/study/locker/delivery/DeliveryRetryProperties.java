package study.locker.delivery;

import jakarta.validation.constraints.Min;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("locker.delivery.retry")
record DeliveryRetryProperties(
        @DefaultValue("500ms") @DurationMin Duration delay,
        @DefaultValue("3") @Min(0) int retries) {}
