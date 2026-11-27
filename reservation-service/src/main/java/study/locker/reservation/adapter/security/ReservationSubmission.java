package study.locker.reservation.adapter.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import study.locker.reservation.application.Caller;
import study.locker.reservation.application.CommandHandler;
import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.reservation.domain.model.Reservation;

import java.util.UUID;

@Component
@Validated
class ReservationSubmission {
    private final CommandHandler commands;

    ReservationSubmission(CommandHandler commands) {
        this.commands = commands;
    }

    public Reservation.View submit(
            Caller caller,
            Action action,
            UUID requestId,
            @NotBlank(message = "A valid ID is required")
                    @Size(max = 100, message = "A valid ID is required")
                    String target,
            String candidate,
            String fingerprint) {
        var command = new CommandHandler.Command(action, requestId, target, candidate, fingerprint);
        return commands.execute(caller, command).result().requireSuccess();
    }
}
