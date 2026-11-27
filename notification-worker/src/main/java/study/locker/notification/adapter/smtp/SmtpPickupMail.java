package study.locker.notification.adapter.smtp;

import jakarta.mail.Message;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import study.locker.delivery.DeliveryUnavailable;
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
        try {
            sender.send(message);
        } catch (MailSendException ex) {
            for (Exception cause : ex.getMessageExceptions()) {
                if (permanentRecipient(cause)) throw ex;
            }
            if (permanentRecipient(ex.getCause())) throw ex;
            throw new DeliveryUnavailable("SMTP", ex);
        } catch (MailAuthenticationException ex) {
            throw new DeliveryUnavailable("SMTP", ex);
        }
    }

    private static boolean permanentRecipient(Throwable failure) {
        for (Throwable ex = failure; ex != null; ex = ex.getCause()) {
            if (ex instanceof SMTPAddressFailedException rejected)
                return rejected.getReturnCode() >= 500;
            if (ex instanceof SendFailedException rejected
                    && rejected.getInvalidAddresses() != null
                    && rejected.getInvalidAddresses().length > 0) return true;
        }
        return false;
    }
}
