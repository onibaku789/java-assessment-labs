package study.locker.edge.adapter.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import study.locker.common.EventLog;
import study.locker.edge.application.GatewayFailure;
import study.locker.edge.application.LockerOperations.Reservation;

public record ObservedLockerOperations(MeterRegistry metrics, String protocol) implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String operation = invocation.getMethod().getName();
        String requestId = argument(invocation, "requestId");
        String reservationId = argument(invocation, "reservationId");
        var sample = Timer.start(metrics);
        String outcome = "INTERNAL";
        try {
            Object result = invocation.proceed();
            if (result instanceof Reservation reservation) reservationId = reservation.id();
            outcome = "OK";
            return result;
        } catch (GatewayFailure failure) {
            outcome = failure.code();
            throw failure;
        } finally {
            sample.stop(Timer.builder("locker.gateway.requests")
                    .tags("protocol", protocol, "operation", operation, "outcome", outcome).publishPercentileHistogram()
                    .register(metrics));
            EventLog.record("gateway_request", operation, outcome, reservationId, requestId, null);
        }
    }

    private static String argument(MethodInvocation invocation, String name) {
        var parameters = invocation.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (name.equals(parameters[i].getName())) return (String) invocation.getArguments()[i];
        }
        return null;
    }
}
