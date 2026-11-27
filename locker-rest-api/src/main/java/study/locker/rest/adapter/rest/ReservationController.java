package study.locker.rest.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import study.locker.edge.application.*;
import study.locker.edge.application.LockerOperations.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class ReservationController {
    private final LockerOperations gateway;

    public ReservationController(LockerOperations gateway) {
        this.gateway = gateway;
    }

    public record Create(String compartmentId) {
    }

    public record Pickup(String code) {
    }

    @GetMapping("/compartments")
    List<Slot> available(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String site,
                         @RequestParam(required = false) String size) {
        return gateway.available(jwt.getTokenValue(), site, size);
    }

    @GetMapping("/reservations/{id}")
    Reservation get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return gateway.get(jwt.getTokenValue(), id);
    }

    @PostMapping("/reservations")
    ResponseEntity<Reservation> reserve(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key,
                                        @RequestBody Create body) {
        Reservation result = gateway.command(jwt.getTokenValue(), Action.RESERVE, key, body.compartmentId(), null);
        return ResponseEntity.created(URI.create("/v1/reservations/" + result.id())).body(result);
    }

    @PostMapping("/reservations/{id}/cancel")
    Reservation cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                       @RequestHeader("Idempotency-Key") String key) {
        return gateway.command(jwt.getTokenValue(), Action.CANCEL, key, id, null);
    }

    @PostMapping("/reservations/{id}/deposit")
    Reservation deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                        @RequestHeader("Idempotency-Key") String key) {
        return gateway.command(jwt.getTokenValue(), Action.DEPOSIT, key, id, null);
    }

    @PostMapping("/reservations/{id}/reissue")
    Reservation reissue(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                        @RequestHeader("Idempotency-Key") String key) {
        return gateway.command(jwt.getTokenValue(), Action.REISSUE, key, id, null);
    }

    @PostMapping("/reservations/{id}/collect")
    Reservation collect(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                        @RequestHeader("Idempotency-Key") String key, @RequestBody Pickup pickup) {
        return gateway.command(jwt.getTokenValue(), Action.COLLECT, key, id, pickup.code());
    }

}
