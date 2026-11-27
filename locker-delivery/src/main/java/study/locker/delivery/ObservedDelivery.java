package study.locker.delivery;

import java.util.Locale;
import java.util.function.Function;

public record ObservedDelivery<T>(
        DeliveryEvents events, Function<T, Correlation> correlation, Operation<T> operation)
        implements DeliveryHandler<T> {
    public record Correlation(String reservation, String event) {}

    @FunctionalInterface
    public interface Operation<T> {
        Enum<?> deliver(T message) throws Exception;
    }

    @Override
    public void deliver(String topic, T message, int attempt) throws Exception {
        var ids = correlation.apply(message);
        if (attempt > 1 && ids.reservation() != null && ids.event() != null)
            events.record(topic, "retried", ids.reservation(), ids.event());
        var outcome = operation.deliver(message);
        events.record(
                topic, outcome.name().toLowerCase(Locale.ROOT), ids.reservation(), ids.event());
    }
}
