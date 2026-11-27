package study.locker.reservation.adapter.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import study.locker.reservation.application.*;
import study.locker.reservation.domain.model.Reservation;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class SecuredReservations implements ReservationAccess {
    private final ReservationQueries queries;
    private final ReservationSubmission submission;
    private final ReservationCommands business;

    SecuredReservations(ReservationQueries queries, ReservationSubmission submission, ReservationCommands business) {
        this.business = business;
        this.queries = queries;
        this.submission = submission;
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.read')")
    public List<Directory.Slot> available(String site, String size) {
        return queries.available(site, size);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.read')")
    public Reservation.View get(String id) {
        Authentication auth = authentication();
        Optional<String> owner = auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_locker.deposit".equals(a.getAuthority())) ? Optional.empty() : Optional.of(
                auth.getName());
        return queries.get(owner, id);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.reserve')")
    public Reservation.View reserve(Request request) {
        return submit(request, business::reserve);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.reserve')")
    public Reservation.View cancel(Request request) {
        return submit(request, business::cancel);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.deposit')")
    public Reservation.View deposit(Request request) {
        return submit(request, (caller, target) -> business.deposit(target));
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.reserve')")
    public Reservation.View reissue(Request request) {
        return submit(request, business::reissue);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_locker.reserve')")
    public Reservation.View collect(Request request, String code) {
        return submit(request, (caller, target) -> business.collect(caller, target, code));
    }

    private Reservation.View submit(Request request, BiFunction<Caller, String, CommandHandler.Result> work) {
        var jwt = ((JwtAuthenticationToken) authentication()).getToken();
        var caller = new Caller(jwt.getSubject(), jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaim("email_verified")));
        return submission.submit(caller, request.operation(), request.parsedId(), request.target(),
                request.fingerprint(), work);
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
