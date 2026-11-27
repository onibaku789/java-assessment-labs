package study.locker.delivery;

/** The record is valid and should stay at the current offset until its destination recovers. */
public final class DeliveryUnavailable extends RuntimeException {
    public DeliveryUnavailable(String destination, Throwable cause) {
        super(destination + " unavailable", cause);
    }

    static boolean causedBy(Throwable failure) {
        for (Throwable ex = failure; ex != null; ex = ex.getCause()) {
            if (ex instanceof DeliveryUnavailable) return true;
        }
        return false;
    }
}
