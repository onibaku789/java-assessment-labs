package study.locker.reservation.application;

public interface CommandFingerprints {
    String create(String input);
    boolean matches(String input, String stored);
}
