package study.locker.edge.adapter.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import study.locker.edge.application.*;

import java.util.List;

public record QuotaOperations(CallerQuota quota, LockerOperations delegate)
        implements LockerOperations {
    private void charge() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
            throw new GatewayFailure("UNAUTHENTICATED", "Missing authenticated identity");
        quota.charge(jwt.getClaimAsString("iss"), jwt.getSubject());
    }

    public List<Slot> available(String credential, String site, String size) {
        charge();
        return delegate.available(credential, site, size);
    }

    public Reservation get(String credential, String id) {
        charge();
        return delegate.get(credential, id);
    }

    public Reservation command(
            String credential, Action action, String requestId, String id, String code) {
        charge();
        return delegate.command(credential, action, requestId, id, code);
    }
}
