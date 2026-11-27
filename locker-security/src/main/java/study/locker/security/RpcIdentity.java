package study.locker.security;

import io.grpc.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public final class RpcIdentity implements ServerInterceptor {
    public static final Metadata.Key<String> AUTH =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<Jwt> ACTOR = Context.key("locker-actor");
    private final JwtDecoder decoder;

    public RpcIdentity(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public record Identity(String subject, String email, boolean verifiedEmail)
            implements Principal {
        @Override
        public String getName() {
            return subject;
        }
    }

    // Bind Spring's thread-local identity only while handling this unary call.
    public static <T> T asAuthenticated(Supplier<T> operation) {
        Jwt actor = ACTOR.get();
        if (actor == null)
            throw Status.UNAUTHENTICATED
                    .withDescription("Valid bearer token required")
                    .asRuntimeException();
        var identity =
                new Identity(
                        actor.getSubject(),
                        actor.getClaimAsString("email"),
                        Boolean.TRUE.equals(actor.getClaim("email_verified")));
        String scopes = actor.getClaimAsString("scope");
        List<SimpleGrantedAuthority> authorities =
                scopes == null
                        ? List.of()
                        : Arrays.stream(scopes.split(" "))
                                .filter(scope -> !scope.isEmpty())
                                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                                .toList();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities));
        var previous = SecurityContextHolder.getDeferredContext();
        try {
            SecurityContextHolder.setContext(context);
            return operation.get();
        } catch (AuthorizationDeniedException denied) {
            String reason =
                    denied.getAuthorizationResult() instanceof ScopeDecision decision
                            ? "Missing scope " + decision.requiredScope()
                            : "Insufficient permission";
            throw Status.PERMISSION_DENIED.withDescription(reason).asRuntimeException();
        } finally {
            SecurityContextHolder.setDeferredContext(previous);
        }
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
        String header = headers.get(AUTH);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jwt actor = decoder.decode(header.substring(7));
                return Contexts.interceptCall(
                        Context.current().withValue(ACTOR, actor), call, headers, next);
            } catch (JwtException ignored) {
                // Use the same response for all invalid tokens.
            }
        }
        call.close(
                Status.UNAUTHENTICATED.withDescription("Valid bearer token required"),
                new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
