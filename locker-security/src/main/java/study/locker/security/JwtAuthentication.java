package study.locker.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;

import java.util.Arrays;
import java.util.List;

final class JwtAuthentication {
    private JwtAuthentication() {
    }

    static UsernamePasswordAuthenticationToken from(Jwt actor) {
        var identity = new RpcIdentity.Identity(actor.getSubject(), actor.getClaimAsString("email"),
                Boolean.TRUE.equals(actor.getClaim("email_verified")));
        String scopes = actor.getClaimAsString("scope");
        List<SimpleGrantedAuthority> authorities =
                scopes == null ? List.of() : Arrays.stream(scopes.split(" ")).filter(scope -> !scope.isEmpty())
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);

    }
}
