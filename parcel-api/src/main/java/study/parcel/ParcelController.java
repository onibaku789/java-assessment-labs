package study.parcel;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/parcels")
class ParcelController {
    record Parcel(String trackingId, String status, String destination) {}
    private final JdbcClient db;

    ParcelController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping
    java.util.List<String> byDestination(@RequestParam String destination) {
        return db.sql("""
                SELECT tracking_id FROM parcel WHERE destination = ? LIMIT 200
                """).param(destination).query(String.class).list();
    }

    @GetMapping("/{id}")
    ResponseEntity<Parcel> track(@PathVariable String id) {
        return ResponseEntity.of(db.sql("""
                SELECT tracking_id, status, destination FROM parcel WHERE tracking_id = ?
                """).param(id).query((row, index) -> new Parcel(
                    row.getString(1), row.getString(2), row.getString(3))).optional());
    }
}
