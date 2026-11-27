package study.locker.edge.adapter.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import study.locker.common.EventLog;
import study.locker.edge.application.GatewayFailure;
import study.locker.edge.application.LockerOperations;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public record ObservedLockerOperations(
        LockerOperations delegate, MeterRegistry metrics, String protocol)
        implements LockerOperations {
    @Override
    public List<Slot> available(String credential, String site, String size) {
        return observe("available", null, null, () -> delegate.available(credential, site, size));
    }

    @Override
    public Reservation get(String credential, String id) {
        return observe("get", null, id, () -> delegate.get(credential, id));
    }

    @Override
    public Reservation command(
            String credential, Action action, String requestId, String id, String code) {
        return observe(
                action.name().toLowerCase(Locale.ROOT),
                requestId,
                action == Action.RESERVE ? null : id,
                () -> delegate.command(credential, action, requestId, id, code));
    }

    private <T> T observe(String operation, String requestId, String id, Supplier<T> call) {
        var sample = Timer.start(metrics);
        String outcome = "INTERNAL";
        try {
            T value = call.get();
            if (value instanceof Reservation booking) id = booking.id();
            outcome = "OK";
            return value;
        } catch (GatewayFailure failure) {
            outcome = failure.code();
            throw failure;
        } finally {
            sample.stop(
                    Timer.builder("locker.gateway.requests")
                            .tags("protocol", protocol, "operation", operation, "outcome", outcome)
                            .publishPercentileHistogram()
                            .register(metrics));
            EventLog.record("gateway_request", operation, outcome, id, requestId, null);
        }
    }
}
