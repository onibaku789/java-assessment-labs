package study.locker.delivery;

@FunctionalInterface
public interface DeliveryHandler<T> {
    void deliver(String topic, T message, int attempt) throws Exception;
}
