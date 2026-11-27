package study.locker.edge.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import study.locker.edge.adapter.observability.ObservedLockerOperations;
import study.locker.edge.adapter.security.CallerQuota;
import study.locker.edge.adapter.security.QuotaOperations;
import study.locker.edge.application.LockerOperations;

import java.lang.reflect.Method;
import java.util.Arrays;

final class GatewayPolicies {
    private GatewayPolicies() {
    }

    static LockerOperations apply(LockerOperations target, MeterRegistry metrics, String protocol, CallerQuota quota) {
        var operations = new NameMatchMethodPointcut();
        operations.setMappedNames(Arrays.stream(LockerOperations.class.getDeclaredMethods())
                .map(Method::getName)
                .toArray(String[]::new));
        var proxy = new ProxyFactory(target);
        proxy.setInterfaces(LockerOperations.class);
        proxy.addAdvisor(new DefaultPointcutAdvisor(operations, new ObservedLockerOperations(metrics, protocol)));
        proxy.addAdvisor(new DefaultPointcutAdvisor(operations, new QuotaOperations(quota)));
        return (LockerOperations) proxy.getProxy();
    }

}
