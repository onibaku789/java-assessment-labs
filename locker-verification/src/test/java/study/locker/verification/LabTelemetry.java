package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static study.locker.verification.StackClient.*;

import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

final class LabTelemetry {
    private static final String GRAFANA = "http://localhost:" + System.getenv().getOrDefault("GRAFANA_PORT", "13000");

    private static JsonNode data(String uid, String path, String expression) throws Exception {
        var result = send("GET", GRAFANA + "/api/datasources/proxy/uid/" + uid + path +
                URLEncoder.encode(expression, StandardCharsets.UTF_8), null, null, null, Map.of());
        assertThat(result.statusCode()).isEqualTo(200);
        return JSON.readTree(result.body()).path("data").path("result");
    }

    private static JsonNode logs(String id) throws Exception {
        return data("locker-loki", "/loki/api/v1/query_range?limit=300&query=", "{job=\"lockers\"} |= \"" + id + "\"");
    }

    private static JsonNode query(String expr) throws Exception {
        return data("locker-prom", "/api/v1/query?query=", expr);
    }

    static double sample(String expression) throws Exception {
        var samples = query(expression);
        return samples.isEmpty() ? 0 : Double.parseDouble(samples.get(0).get("value").get(1).asString());
    }

    static void assertPickup(String reservation, String code, String wrong, String counter,
                             double initial) throws Exception {
        await().ignoreExceptions().atMost(Duration.ofSeconds(70)).untilAsserted(() -> {
            assertLogs(reservation, code, wrong);
            assertThat(sample(counter)).isGreaterThan(initial);
            assertThat(query("locker_outbox_pending").size()).isOne();
            assertThat(query("kafka_consumergroup_lag{consumergroup=~\"locker-.*\"}").size()).isGreaterThanOrEqualTo(2);
            for (var series : query("{__name__=~\"locker_.*\"}")) {
                assertThat(series.path("metric").propertyNames()).doesNotContain("reservation_id", "event_id",
                        "request_id", "email", "code");
            }
        });
    }

    static void assertLogs(String reservation, String code, String wrong) throws Exception {
        var lines = logs(reservation);
        var services = new HashSet<String>();
        var outcomes = new HashSet<String>();
        for (var stream : lines) {
            services.add(stream.path("stream").path("service").asString());
            for (var pair : stream.path("values"))
                outcomes.add(JSON.readTree(pair.get(1).asString()).path("outcome").asString());
        }
        assertThat(services).contains("rest", "graphql", "soap", "reservations", "notifications", "receipts");
        assertThat(outcomes).contains("committed", "replayed", "sent", "stored", "PERMISSION_DENIED",
                "FAILED_PRECONDITION");
        String raw = lines.toString();
        boolean leaked = List.of(code, wrong, "lab-customer@example.test", "Bearer ", "envelope", "pickup.key").stream()
                .anyMatch(raw::contains);
        assertThat(leaked).as("sensitive fields excluded from reservation events").isFalse();
    }
}
