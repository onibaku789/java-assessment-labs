package study.locker.reservation.bootstrap;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("locker.outbox")
record OutboxProperties(
        @Positive int batchSize,
        @NotNull @DurationMin(inclusive = false) Duration ackTimeout,
        @NotNull @DurationMin(millis = 1) Duration pollInterval) {}
