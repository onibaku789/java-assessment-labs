package study.locker.edge.adapter.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import study.locker.edge.application.GatewayFailure;

public final class CurrentCaller {
    private CurrentCaller() {
    }

    static Jwt jwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new GatewayFailure("UNAUTHENTICATED", "Missing authenticated identity");
        }
        return jwt;
    }

    public static String tokenValue() {
        return jwt().getTokenValue();
    }
}
