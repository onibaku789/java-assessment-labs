package study.locker.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import study.locker.notification.domain.model.PickupNotification;

import java.time.*;

class DeliverPickupNotificationTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final DeliverPickupNotification.Decryptor decryptor =
            mock(DeliverPickupNotification.Decryptor.class);
    private final DeliverPickupNotification.Mail mail = mock(DeliverPickupNotification.Mail.class);
    private final DeliverPickupNotification delivery =
            new DeliverPickupNotification(Clock.fixed(now, ZoneOffset.UTC), decryptor, mail);

    @Test
    void expirySkipsBothDecryptionAndDelivery() throws Exception {
        assertThat(delivery.deliver(noticeExpiringAt(now)))
                .isEqualTo(DeliverPickupNotification.Outcome.EXPIRED);
        verifyNoInteractions(decryptor, mail);
    }

    @Test
    void validMailIsDecryptedForItsEventAndSentToTheRecipient() throws Exception {
        var notice = noticeExpiringAt(now.plusSeconds(1));
        when(decryptor.open(notice.eventId(), notice.envelope())).thenReturn("01234567");
        assertThat(delivery.deliver(notice)).isEqualTo(DeliverPickupNotification.Outcome.SENT);
        verify(mail).send(notice, "01234567");
    }

    private PickupNotification noticeExpiringAt(Instant expiresAt) {
        return new PickupNotification(
                1,
                "event",
                "reservation",
                "customer@example.test",
                expiresAt.toString(),
                "envelope");
    }
}
