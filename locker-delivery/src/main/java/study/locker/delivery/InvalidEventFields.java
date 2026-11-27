package study.locker.delivery;

public final class InvalidEventFields extends RuntimeException {
    InvalidEventFields(String fields) {
        super("Invalid event fields: " + fields);
    }
}
