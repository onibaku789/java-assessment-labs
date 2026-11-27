package study.locker.notification.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSender;

import study.locker.common.PickupSecrets;
import study.locker.delivery.*;
import study.locker.notification.adapter.smtp.SmtpPickupMail;
import study.locker.notification.application.DeliverPickupNotification;
import study.locker.notification.domain.model.PickupNotification;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Import(DeliveryConfig.class)
class NotificationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DeliverPickupNotification delivery(
            Clock clock,
            JavaMailSender sender,
            @Value("${locker.secret:/run/locker/pickup.key}") String path,
            @Value("${locker.mail.from:lockers@example.test}") String from)
            throws IOException {
        var secrets = new PickupSecrets(Files.readString(Path.of(path)));
        return new DeliverPickupNotification(
                clock, secrets::open, new SmtpPickupMail(sender, from));
    }

    @Bean
    DeliveryHandler<PickupNotification> handler(
            DeliveryEvents events, DeliverPickupNotification useCase) {
        return new ObservedDelivery<>(
                events,
                msg -> new ObservedDelivery.Correlation(msg.reservationId(), msg.eventId()),
                useCase::deliver);
    }
}
