package study.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="parcel.region=test-yard")
@AutoConfigureMockMvc
class ReleaseLabTest {
    @Autowired MockMvcTester http;

    @Test void reportsConfiguredRegion() {
        assertThat(http.get().uri("/track")).hasStatusOk()
                .bodyJson().extractingPath("$.region").isEqualTo("test-yard");
    }

    @Test void readyByDefault() {
        assertThat(http.get().uri("/actuator/health/readiness")).hasStatusOk();
    }
}
