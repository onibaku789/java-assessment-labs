package study.locker.notification.adapter.crypto;

import study.locker.common.PickupTransit;
import study.locker.delivery.DeliveryUnavailable;
import study.locker.notification.application.DeliverPickupNotification.Decryptor;

public record TransitDecryptor(PickupTransit transit) implements Decryptor {
    public String open(String eventId, String envelope) {
        try {
            return transit.open(eventId, envelope);
        } catch (PickupTransit.Unavailable failure) {
            throw new DeliveryUnavailable("Vault", failure);
        }
    }
}
