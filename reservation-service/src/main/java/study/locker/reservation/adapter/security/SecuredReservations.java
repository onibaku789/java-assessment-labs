package study.locker.reservation.adapter.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import study.locker.reservation.application.*;
import study.locker.reservation.domain.model.Reservation;
import study.locker.security.RpcIdentity.Identity;

import java.util.List;
import java.util.Optional;

@Component
public class SecuredReservations implements ReservationAccess {
    private final ReservationQueries queries;
    private final ReservationSubmission submission;

    SecuredReservations(ReservationQueries queries, ReservationSubmission submission) {
        this.queries = queries;
        this.submission = submission;
    }

    @Override
    @PreAuthorize("@reservationScopes.read(authentication)")
    public List<Directory.Slot> available(String site, String size) {
        return queries.available(site, size);
    }

    @Override
    @PreAuthorize("@reservationScopes.read(authentication)")
    public Reservation.View get(String id) {
        Authentication auth = authentication();
        Optional<String> owner =
                ReservationScopes.canReadAnyReservation(auth)
                        ? Optional.empty()
                        : Optional.of(auth.getName());
        return queries.get(owner, id);
    }

    @Override
    @PreAuthorize("@reservationScopes.command(authentication, #action)")
    public Reservation.View execute(ReservationCommands.Action action, Request request) {
        var identity = (Identity) authentication().getPrincipal();
        var caller = new Caller(identity.subject(), identity.email(), identity.verifiedEmail());
        return submission.submit(
                caller,
                action,
                request.parsedId(),
                request.target(),
                request.candidate(),
                request.fingerprint());
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
