package study.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:parcels",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc(addFilters = false)
class ParcelApiTest extends ParcelTestKeys {
    @Autowired MockMvc http;

    @Test void tracksKnownParcel() throws Exception {
        http.perform(get("/parcels/P-100")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test void unknownParcelReturns404() throws Exception {
        http.perform(get("/parcels/missing")).andExpect(status().isNotFound());
    }
}
