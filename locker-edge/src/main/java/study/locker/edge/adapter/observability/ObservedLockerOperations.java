package study.locker.edge.adapter.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import study.locker.common.EventLog;
import study.locker.edge.application.GatewayFailure;
import study.locker.edge.application.LockerOperations.Reservation;

import java.util.stream.IntStream;

@Aspect
@Order(1)
public record ObservedLockerOperations(MeterRegistry metrics, @Value("${locker.protocol:gateway}") String protocol) {
    @Around("execution(public * study.locker.edge.application.LockerOperations.*(..))")
    public Object observe(ProceedingJoinPoint invocation) throws Throwable {
        String operation = invocation.getSignature().getName();
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

    private static String argument(ProceedingJoinPoint invocation, String name) {
        var parameters = ((MethodSignature) invocation.getSignature()).getMethod().getParameters();
        var index = IntStream.range(0, parameters.length)
                .filter(i -> name.equals(parameters[i].getName()))
                .findFirst();
        return index.isPresent() ? (String) invocation.getArgs()[index.getAsInt()] : null;
    }
}
