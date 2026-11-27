package study.locker.edge.adapter.security;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public record QuotaOperations(CallerQuota quota) implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        var jwt = CurrentCaller.jwt();
        quota.charge(jwt.getClaimAsString("iss"), jwt.getSubject());
        return invocation.proceed();
    }
}
