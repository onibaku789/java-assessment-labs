package study.locker.edge.adapter.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;

@Aspect
@Order(2)
public record QuotaOperations(CallerQuota quota) {
    @Before("execution(public * study.locker.edge.application.LockerOperations.*(..))")
    public void chargeCaller() {
        var jwt = CurrentCaller.jwt();
        quota.charge(jwt.getClaimAsString("iss"), jwt.getSubject());
    }
}
