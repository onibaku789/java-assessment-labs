package study.parcel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(value = "/parcels", version = "1.0")
class ParcelController {
    record Parcel(@NotBlank @Size(max = 32) @Pattern(regexp = "[^/]+", message = "trackingId cannot contain /") String trackingId,
                  @Size(max = 32) @NotBlank String status,
                  @NotBlank @Size(max = 64) String destination,
                  Instant createdAt)  {
        Parcel(String trackingId, String status, String destination) {
            this(trackingId, status, destination, Instant.now());
        }
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
                SELECT tracking_id, status, destination, created_at FROM parcel WHERE tracking_id = ?
                """).param(id).query((row, _) -> new Parcel(
                row.getString("tracking_id"), row.getString("status"), row.getString("destination"),
                row.getTimestamp("created_at").toInstant())).optional());
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
