package study.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="parcel.region=test-yard")
@AutoConfigureMockMvc
class ReleaseLabTest {
    @Autowired MockMvc http;

    @Test void reportsConfiguredRegion() throws Exception {
        http.perform(get("/track")).andExpect(status().isOk())
            .andExpect(jsonPath("$.region").value("test-yard"));
    }

    @Test void readyByDefault() throws Exception {
        http.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
