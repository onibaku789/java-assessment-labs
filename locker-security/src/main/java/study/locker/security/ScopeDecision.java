package study.locker.security;

import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

public record ScopeDecision(String requiredScope, boolean granted) implements AuthorizationResult {
    public static ScopeDecision require(Authentication authentication, String scope) {
        String authority = "SCOPE_" + scope;
        return new ScopeDecision(
                scope,
                authentication.getAuthorities().stream()
                        .anyMatch(a -> authority.equals(a.getAuthority())));
    }

    @Override
    public boolean isGranted() {
        return granted;
    }
}
