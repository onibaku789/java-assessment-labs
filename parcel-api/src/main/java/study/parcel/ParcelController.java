package study.parcel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/parcels")
class ParcelController {
    record Parcel(@NotBlank @Size(max = 32) String trackingId, @Size(max = 32) @NotBlank String status, @NotBlank @Size(max = 64) String destination) {
    }

    private final JdbcClient db;

    ParcelController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping
    List<String> byDestination(@RequestParam String destination) {
        return db.sql("""
                SELECT tracking_id FROM parcel WHERE destination = ? LIMIT 200
                """).param(destination).query(String.class).list();
    }

    @GetMapping("/{id}")
    ResponseEntity<Parcel> track(@PathVariable String id) {
        return ResponseEntity.of(db.sql("""
                SELECT tracking_id, status, destination FROM parcel WHERE tracking_id = ?
                """).param(id).query((row, _) -> new Parcel(
                row.getString(1), row.getString(2), row.getString(3))).optional());
    }

    @PostMapping
    ResponseEntity<Void> create(@Valid @RequestBody Parcel parcel) {
        URI location = UriComponentsBuilder.fromPath("/parcels/{id}")
                .build(parcel.trackingId());

        try {
            db.sql("INSERT INTO parcel (tracking_id, status, destination) VALUES (?, ?, ?)")
                    .param(parcel.trackingId())
                    .param(parcel.status())
                    .param(parcel.destination())
                    .update();
        } catch (DuplicateKeyException duplicate) {
            return ResponseEntity.status(HttpStatus.CONFLICT).location(location).build();
        }

        return ResponseEntity.created(location).build();
    }

}
