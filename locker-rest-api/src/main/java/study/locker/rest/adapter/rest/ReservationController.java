package study.locker.rest.adapter.rest;

import org.springframework.http.ResponseEntity;
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
    List<Slot> available(@RequestParam(required = false) String site, @RequestParam(required = false) String size) {
        return gateway.available(site, size);
    }

    @GetMapping("/reservations/{id}")
    Reservation get(@PathVariable String id) {
        return gateway.get(id);
    }

    @PostMapping("/reservations")
    ResponseEntity<Reservation> reserve(@RequestHeader("Idempotency-Key") String key, @RequestBody Create body) {
        Reservation result = gateway.reserve(key, body.compartmentId());
        return ResponseEntity.created(URI.create("/v1/reservations/" + result.id())).body(result);
    }

    @PostMapping("/reservations/{id}/cancel")
    Reservation cancel(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return gateway.cancel(key, id);
    }

    @PostMapping("/reservations/{id}/deposit")
    Reservation deposit(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return gateway.deposit(key, id);
    }

    @PostMapping("/reservations/{id}/reissue")
    Reservation reissue(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return gateway.reissue(key, id);
    }

    @PostMapping("/reservations/{id}/collect")
    Reservation collect(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
                        @RequestBody Pickup pickup) {
        return gateway.collect(key, id, pickup.code());
    }

}
