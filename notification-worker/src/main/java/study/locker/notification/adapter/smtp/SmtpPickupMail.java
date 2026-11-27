package study.locker.notification.adapter.smtp;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;

import org.springframework.mail.javamail.JavaMailSender;

import study.locker.notification.application.DeliverPickupNotification;
import study.locker.notification.domain.model.PickupNotification;

public record SmtpPickupMail(JavaMailSender sender, String from)
        implements DeliverPickupNotification.Mail {
    public void send(PickupNotification notice, String code) throws Exception {
        var message = sender.createMimeMessage();
        message.setFrom(new InternetAddress(from, true));
        message.setRecipient(
                Message.RecipientType.TO, new InternetAddress(notice.recipient(), true));
        message.setSubject("Pickup ready for " + notice.reservationId());
        message.setText("Pickup code: " + code + "\nValid until " + notice.expiresAt());
        message.setHeader("Message-ID", "<" + notice.eventId() + "@lockers.example.test>");
        sender.send(message);
    }
}
