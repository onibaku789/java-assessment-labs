package study.locker.reservation.adapter.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import study.locker.reservation.application.CommandHandler;
import study.locker.reservation.domain.model.Reservation;

import java.util.function.Function;

@Component
@Validated
class ReservationSubmission {
    private final CommandHandler commands;

    ReservationSubmission(CommandHandler commands) {
        this.commands = commands;
    }

    public Reservation.View submit(CommandHandler.Command command,
                                   @NotBlank(message = "A valid ID is required")
                                   @Size(max = 100, message = "A valid ID is required") String target,
                                   Function<String, CommandHandler.Result> work) {
        return commands.execute(command, () -> work.apply(target)).result().requireSuccess();
    }
}
