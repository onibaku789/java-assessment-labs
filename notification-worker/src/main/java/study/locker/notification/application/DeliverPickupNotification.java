package study.locker.notification.application;

import study.locker.notification.domain.model.PickupNotification;

import java.time.Clock;

public record DeliverPickupNotification(Clock clock, Decryptor decryptor, Mail mail) {
    public interface Decryptor {
        String open(String eventId, String envelope);
    }

    public interface Mail {
        void send(PickupNotification notice, String code) throws Exception;
    }

    public enum Outcome {
        SENT,
        EXPIRED
    }

    public Outcome deliver(PickupNotification notice) throws Exception {
        if (notice.expiredAt(clock.instant())) return Outcome.EXPIRED;
        mail.send(notice, decryptor.open(notice.eventId(), notice.envelope()));
        return Outcome.SENT;
    }
}
