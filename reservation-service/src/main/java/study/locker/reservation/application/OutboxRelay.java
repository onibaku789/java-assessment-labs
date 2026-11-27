package study.locker.reservation.application;

public interface OutboxRelay {
    void publish();
}
