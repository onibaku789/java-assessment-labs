package study.locker.notification.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.vault.core.VaultOperations;
import study.locker.common.PickupTransit;
import study.locker.notification.adapter.crypto.TransitDecryptor;
import study.locker.delivery.*;
import study.locker.notification.adapter.smtp.SmtpPickupMail;
import study.locker.notification.application.DeliverPickupNotification;
import study.locker.notification.domain.model.PickupNotification;

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
            VaultOperations vault,
            @Value("${locker.mail.from:lockers@example.test}") String from) {
        return new DeliverPickupNotification(
                clock, new TransitDecryptor(new PickupTransit(vault)), new SmtpPickupMail(sender, from));
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
