package study.locker.rest.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import study.locker.edge.application.*;

import java.util.Map;

@RestControllerAdvice(assignableTypes = ReservationController.class)
class ReservationErrors {
    private static final Map<String, Integer> HTTP_STATUS = Map.of("INVALID_ARGUMENT", 400, "NOT_FOUND", 404,
            "PERMISSION_DENIED", 403, "UNAUTHENTICATED", 401, "FAILED_PRECONDITION", 409, "ALREADY_EXISTS", 409,
            "RESOURCE_EXHAUSTED", 429, "UNAVAILABLE", 503, "DEADLINE_EXCEEDED", 504);

    @ExceptionHandler(GatewayFailure.class)
    ResponseEntity<Map<String, String>> failure(GatewayFailure ex) {
        String code = ex.code();
        var response = ResponseEntity.status(HTTP_STATUS.getOrDefault(code, 500));
        if (ex.retryAfterSeconds() > 0) response.header("Retry-After", Long.toString(ex.retryAfterSeconds()));
        return response.body(Map.of("code", code, "message", java.util.Objects.toString(ex.getMessage(), code)));
    }
}
