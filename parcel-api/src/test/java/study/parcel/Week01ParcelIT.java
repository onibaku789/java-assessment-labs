package study.parcel;

import java.net.URI;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:week01",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Disabled("Enable when starting Week 1")
class Week01ParcelIT extends ParcelTestKeys {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;

    private ResultActions create(String id, String destination) throws Exception {
        return http.perform(post("/parcels").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"trackingId":"%s","status":"IN_TRANSIT","destination":"%s"}
                        """.formatted(id, destination)));
    }

    @Test void createReturns201AndLocationThatRetrievesThePersistedParcel() throws Exception {
        var response = create("P-201", "Leeds").andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse();
        URI location = URI.create(response.getHeader("Location"));
        http.perform(get(URI.create("http://localhost/parcels").resolve(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("P-201"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.destination").value("Leeds"));
        assertThat(db.queryForObject("SELECT destination FROM parcel WHERE tracking_id = ?",
                String.class, "P-201")).isEqualTo("Leeds");
    }

    @Test void replayingTheSameCreationLeavesExactlyOneParcel() throws Exception {
        create("P-202", "York").andExpect(status().isCreated());
        int replay = create("P-202", "York").andReturn().getResponse().getStatus();
        assertThat(replay).as("Choose an idempotent success (200/204) or an explicit conflict (409)")
                .isIn(200, 204, 409);
        assertThat(db.queryForObject("SELECT count(*) FROM parcel WHERE tracking_id = ?",
                Integer.class, "P-202")).isEqualTo(1);
    }

    @Test void blankDestinationIsRejectedBeforeInsertingTheRow() throws Exception {
        int code = create("P-203", "  ").andReturn().getResponse().getStatus();
        assertThat(code).as("Invalid destination should produce a client validation error").isIn(400, 422);
        assertThat(db.queryForObject("SELECT count(*) FROM parcel WHERE tracking_id = ?",
                Integer.class, "P-203")).isZero();
    }

    @Test void theOriginalClientCanStillReadAllThreeFields() throws Exception {
        http.perform(get("/parcels/P-100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("P-100"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.destination").value("York"));
    }
}
