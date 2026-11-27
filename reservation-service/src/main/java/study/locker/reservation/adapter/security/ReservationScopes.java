package study.locker.reservation.adapter.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.security.ScopeDecision;

@Component("reservationScopes")
public class ReservationScopes {
    public ScopeDecision read(Authentication authentication) {
        return ScopeDecision.require(authentication, "locker.read");
    }

    public ScopeDecision command(Authentication authentication, Action action) {
        return ScopeDecision.require(
                authentication,
                switch (action) {
                    case DEPOSIT -> "locker.deposit";
                    case RESERVE, CANCEL, COLLECT, REISSUE -> "locker.reserve";
                });
    }

    static boolean canReadAnyReservation(Authentication authentication) {
        return ScopeDecision.require(authentication, "locker.deposit").isGranted();
    }
}
