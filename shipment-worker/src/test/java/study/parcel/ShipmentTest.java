package study.parcel;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ShipmentTest {
    @Test void readsSyntheticFixture() throws Exception {
        var shipment = Worker.JSON.readValue(Path.of("fixtures/delivered.json").toFile(), Shipment.class);
        assertThat(shipment.eventId()).isEqualTo("evt-7");
    }

    @Test void rejectsMissingEventIdentity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Shipment("", "P-100", "DELIVERED"));
    }
}
