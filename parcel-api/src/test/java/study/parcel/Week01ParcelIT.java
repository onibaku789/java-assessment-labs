package study.parcel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtensionConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Keep the enclosing MockMvc fields bound to the H2 context when JUnit runs a nested PostgreSQL test.
@SpringExtensionConfig(useTestClassScopedExtensionContext = true)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:week01",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class Week01ParcelIT extends ParcelTestKeys {
    @Autowired
    MockMvc http;
    @Autowired
    JdbcTemplate db;
    @Autowired
    ObjectMapper json;

    private ResultActions create(String id, String destination) throws Exception {
        return postParcel(json.writeValueAsString(Map.of(
                "trackingId", id, "status", "IN_TRANSIT", "destination", destination)));
    }

    private ResultActions postParcel(String body) throws Exception {
        return http.perform(post("/parcels").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createReturns201AndLocationThatRetrievesThePersistedParcel() throws Exception {
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

    @Test
    void replayingTheSameCreationLeavesExactlyOneParcel() throws Exception {
        create("P-202", "York").andExpect(status().isCreated());
        int replay = create("P-202", "York").andReturn().getResponse().getStatus();
        assertThat(replay).as("Choose an idempotent success (200/204) or an explicit conflict (409)")
                .isIn(200, 204, 409);
        assertThat(db.queryForObject("SELECT count(*) FROM parcel WHERE tracking_id = ?",
                Integer.class, "P-202")).isEqualTo(1);
    }

    @Test
    void conflictingDestinationLeavesTheFirstParcelIntact() throws Exception {
        create("P-CONFLICT", "York").andExpect(status().isCreated());
        create("P-CONFLICT", "Leeds").andExpect(status().isConflict());
        http.perform(get("/parcels/P-CONFLICT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("York"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"trackingId, omitted", "trackingId, null", "status, omitted",
            "status, null", "destination, omitted", "destination, null"})
    void missingRequiredValueIsRejectedBeforePersistence(String field, String form) throws Exception {
        var body = json.createObjectNode().put("trackingId", "P-REQUIRED")
                .put("status", "IN_TRANSIT").put("destination", "York");
        if ("omitted".equals(form)) {
            body.remove(field);
        } else {
            body.putNull(field);
        }
        int before = db.queryForObject("SELECT count(*) FROM parcel", Integer.class);
        int code = postParcel(json.writeValueAsString(body)).andReturn().getResponse().getStatus();
        assertAll(
                () -> assertThat(code).isIn(400, 422),
                () -> assertThat(db.queryForObject("SELECT count(*) FROM parcel", Integer.class)).isEqualTo(before));
    }

    @Test
    void blankDestinationIsRejectedBeforeInsertingTheRow() throws Exception {
        int code = create("P-203", "  ").andReturn().getResponse().getStatus();
        assertThat(code).as("Invalid destination should produce a client validation error").isIn(400, 422);
        assertThat(db.queryForObject("SELECT count(*) FROM parcel WHERE tracking_id = ?",
                Integer.class, "P-203")).isZero();
    }

    @Test
    void theOriginalClientCanStillReadAllThreeFields() throws Exception {
        http.perform(get("/parcels/P-100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("P-100"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.destination").value("York"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OriginalParcel(String trackingId, String status, String destination) {}

    @Test
    void originalClientCanReadAResponseWithAnAdditionalField() throws Exception {
        create("P-VERSION", "Leeds").andExpect(status().isCreated());
        var examples = List.of(new OriginalParcel("P-100", "IN_TRANSIT", "York"),
                new OriginalParcel("P-VERSION", "IN_TRANSIT", "Leeds"));
        var originalFields = Set.of("trackingId", "status", "destination");
        boolean sawAdditionalField = false;
        for (var expected : examples) {
            var body = http.perform(get("/parcels/{id}", expected.trackingId())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            var response = json.readTree(body);
            assertThat(json.treeToValue(response, OriginalParcel.class)).isEqualTo(expected);
            if (!originalFields.containsAll(response.propertyNames())) {
                sawAdditionalField = true;
            }
        }
        assertThat(sawAdditionalField).as("Expose your optional response field on the seeded parcel or the newly created one")
                .isTrue();
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "spring.datasource.url=jdbc:tc:postgresql:19.0-alpine:///parcels",
            "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
    })
    class PostgresPersistence extends ParcelTestKeys {
        private static final Duration TIMEOUT = Duration.ofSeconds(15);
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        @LocalServerPort
        int port;
        @Autowired
        JdbcTemplate db;
        @Autowired
        ObjectMapper json;

        @AfterEach
        void closeClient() {
            http.close();
        }

        private HttpRequest create(String id, String status, String destination) {
            var body = json.writeValueAsString(Map.of("trackingId", id, "status", status, "destination", destination));
            return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/parcels")).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        }

        private int rows(String id) {
            return db.queryForObject("SELECT count(*) FROM parcel WHERE tracking_id = ?", Integer.class, id);
        }

        @Test
        void lookupReturnsTheStoredCreationTime() throws Exception {
            var createdAt = Instant.parse("2025-03-12T09:15:30Z");
            db.update("INSERT INTO parcel (tracking_id, status, destination, created_at) VALUES (?, ?, ?, ?)",
                    "P-CREATED-AT", "IN_TRANSIT", "York", Timestamp.from(createdAt));

            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/parcels/P-CREATED-AT"))
                    .timeout(TIMEOUT).GET().build();
            var response = http.send(request, ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(Instant.parse(json.readTree(response.body()).required("createdAt").asString())).isEqualTo(createdAt);
        }

        @Test
        void simultaneousCreatesMatchTheSequentialReplayPolicyAndStoreOneRow() throws Exception {
            var request = create("P-CONCURRENT", "IN_TRANSIT", "York");
            var replies = new ArrayList<CompletableFuture<HttpResponse<String>>>();
            try (var connection = db.getDataSource().getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                try {
                    // Reads proceed while inserts wait, so competing requests can both see a missing ID.
                    statement.execute("LOCK TABLE parcel IN SHARE MODE");
                    for (int i = 0; i < 8; i++) {
                        replies.add(http.sendAsync(request, ofString()));
                    }
                    waitForOverlappingWriters(statement);
                } finally {
                    connection.rollback(); // Release the test lock before waiting for the HTTP responses.
                }
            }
            var statuses = replies.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).toList();
            int replay = http.send(request, ofString()).statusCode();
            assertThat(replay).as("the API's sequential duplicate policy").isIn(200, 204, 409);
            assertAll(
                    () -> assertThat(statuses).as("one creator and seven replays").containsOnly(201, replay),
                    () -> assertThat(statuses).filteredOn(code -> code == 201).hasSize(1),
                    () -> assertThat(rows("P-CONCURRENT")).isOne());
        }

        private static void waitForOverlappingWriters(Statement statement) {
            try {
                await().pollInSameThread().pollInterval(Duration.ofMillis(20)).atMost(Duration.ofSeconds(3))
                        .until(() -> {
                            try (var waits = statement.executeQuery(
                                    "SELECT count(*) FROM pg_locks WHERE relation = 'parcel'::regclass AND NOT granted")) {
                                waits.next();
                                return waits.getInt(1) >= 2;
                            }
                        });
            } catch (ConditionTimeoutException timeout) {
                // A writer may serialize requests before they reach the table lock. Release it and check the responses.
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"P 101", "P?102", "P/103"})
        void trackingIdIsRejectedOrHasARetrievableLocation(String id) throws Exception {
            var request = create(id, "IN_TRANSIT", "Leeds");
            var created = http.send(request, ofString());
            if (created.statusCode() == 201) {
                var location = created.headers().firstValue("Location").orElseThrow();
                var get = HttpRequest.newBuilder(request.uri().resolve(location)).timeout(TIMEOUT).GET().build();
                var fetched = http.send(get, ofString());
                assertThat(fetched.statusCode()).as("GET %s for ID '%s'", location, id).isEqualTo(200);
                assertThat(json.readTree(fetched.body()).required("trackingId").asString()).isEqualTo(id);
            } else {
                assertAll(
                        () -> assertThat(created.statusCode()).as("validation response for '%s'", id).isIn(400, 422),
                        () -> assertThat(rows(id)).as("rows saved for a rejected ID").isZero());
            }
        }

        static Stream<Arguments> oversizedParcels() {
            return Stream.of(
                    Arguments.of("trackingId", "P".repeat(33), "IN_TRANSIT", "Leeds"),
                    Arguments.of("status", "P-LONG-STATUS", "S".repeat(33), "Leeds"),
                    Arguments.of("destination", "P-LONG-DEST", "IN_TRANSIT", "D".repeat(65)));
        }

        @Test
        void trackingIdAndDestinationAtTheirLengthLimitsRoundTrip() throws Exception {
            String id = "P-" + "1".repeat(30);
            String destination = "D".repeat(64);
            var request = create(id, "IN_TRANSIT", destination);
            var created = http.send(request, ofString());
            assertThat(created.statusCode()).as("32-character tracking ID and 64-character destination").isEqualTo(201);
            var location = request.uri().resolve(created.headers().firstValue("Location").orElseThrow());
            var fetched = http.send(HttpRequest.newBuilder(location).timeout(TIMEOUT).GET().build(), ofString());
            assertThat(fetched.statusCode()).isEqualTo(200);
            var parcel = json.readTree(fetched.body());
            assertThat(parcel.required("trackingId").asString()).isEqualTo(id);
            assertThat(parcel.required("destination").asString()).isEqualTo(destination);
        }

        @ParameterizedTest(name = "{0} exceeds its column limit")
        @MethodSource("oversizedParcels")
        void oversizedFieldIsRejectedWithoutSaving(String field, String id, String status, String destination) throws Exception {
            var response = http.send(create(id, status, destination), ofString());
            assertAll(
                    () -> assertThat(response.statusCode()).as("validation response for oversized %s", field).isIn(400, 422),
                    () -> assertThat(rows(id)).as("rows saved for invalid input").isZero());
        }
    }
}
