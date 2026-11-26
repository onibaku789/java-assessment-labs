package study.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:parcels",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc(addFilters = false)
class ParcelApiTest extends ParcelTestKeys {
    @Autowired MockMvcTester http;

    @Test void tracksKnownParcel() {
        assertThat(http.get().uri("/parcels/P-100")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("IN_TRANSIT");
    }

    @Test void unknownParcelReturns404() {
        assertThat(http.get().uri("/parcels/missing")).hasStatus(404);
    }
}
