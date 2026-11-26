package study.parcel;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ShipmentTest {
    @Test void readsSyntheticFixture() throws Exception {
        var shipment = Worker.JSON.readValue(Path.of("fixtures/delivered.json").toFile(), Shipment.class);
        assertEquals("evt-7", shipment.eventId());
    }

    @Test void rejectsMissingEventIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new Shipment("", "P-100", "DELIVERED"));
    }
}
