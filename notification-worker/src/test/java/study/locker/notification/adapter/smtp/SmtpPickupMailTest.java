package study.locker.notification.adapter.smtp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import study.locker.delivery.DeliveryUnavailable;
import study.locker.notification.domain.model.PickupNotification;

import java.util.Map;
import java.util.Properties;

class SmtpPickupMailTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpPickupMail adapter = new SmtpPickupMail(sender, "locker@example.test");
    private final PickupNotification notice =
            new PickupNotification(
                    1,
                    "event",
                    "reservation",
                    "customer@example.test",
                    "2026-01-01T12:00:00Z",
                    "envelope");

    @Test
    void temporaryMailboxRejectionLeavesTheDeliveryPending() throws Exception {
        var failure = rejectRecipient(450);
        assertThatThrownBy(() -> adapter.send(notice, "12345678"))
                .isInstanceOf(DeliveryUnavailable.class)
                .hasCause(failure);
    }

    @Test
    void permanentMailboxRejectionIsEligibleForDeadLettering() throws Exception {
        var failure = rejectRecipient(550);
        assertThatThrownBy(() -> adapter.send(notice, "12345678")).isSameAs(failure);
    }

    private MailSendException rejectRecipient(int code) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        var rejection =
                new SMTPAddressFailedException(
                        new InternetAddress(notice.recipient()),
                        "RCPT TO",
                        code,
                        "Mailbox rejected");
        var failure = new MailSendException("Send failed", null, Map.of(message, rejection));
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(failure).when(sender).send(message);
        return failure;
    }
}
