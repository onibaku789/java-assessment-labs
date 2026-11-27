package study.locker.delivery;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.*;

import java.lang.annotation.*;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = EventTimestamp.Check.class)
public @interface EventTimestamp {
    String message() default "must be an instant";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Check implements ConstraintValidator<EventTimestamp, String> {
        @Override
        public boolean isValid(String text, ConstraintValidatorContext context) {
            if (text == null) return false;
            try {
                Instant.parse(text);
                return true;
            } catch (DateTimeParseException invalid) {
                return false;
            }
        }
    }
}
