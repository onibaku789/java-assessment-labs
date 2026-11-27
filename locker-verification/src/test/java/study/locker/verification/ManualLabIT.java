package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import static study.locker.verification.StackClient.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.Select;

import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@EnabledIfSystemProperty(named = "locker.manual", matches = "true")
class ManualLabIT {
    private static final String LAB =
            "http://localhost:" + System.getenv().getOrDefault("LAB_PORT", "18080");
    private static final String GRAFANA =
            "http://localhost:" + System.getenv().getOrDefault("GRAFANA_PORT", "13000");
    private WebDriver driver;

    @Test
    void browserReceivesOnlyPublicSettings() throws Exception {
        var response = send("GET", LAB + "/inspect/config", null, null, null, Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        var config = JSON.readTree(response.body());
        assertThat(config.propertyNames())
                .containsExactlyInAnyOrder(
                        "refreshMs",
                        "requestTimeoutMs",
                        "historyLimit",
                        "retentionHours",
                        "grafanaUrl",
                        "mailpitUrl");
        assertThat(config.path("historyLimit").asInt()).isPositive();
        assertThat(config.path("retentionHours").asInt()).isGreaterThanOrEqualTo(24);
    }

    @Test
    void guidedPickupIsVisibleInLogsAndMetrics() throws Exception {
        String counter =
                "sum(locker_gateway_requests_seconds_count{protocol=\"graphql\",operation=\"collect\",outcome=\"OK\"})";
        // A persisted Prometheus store can still hold the previous gateway's counter after a
        // restart.
        long started = java.time.Instant.now().getEpochSecond();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> sample("timestamp(up{service=\"graphql\"} == 1)") > started);
        double initial = sample(counter);
        var options =
                new ChromeOptions()
                        .addArguments(
                                "--headless=new",
                                "--window-size=1440,1080",
                                "--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        String id = null;
        try {
            driver.get(LAB);
            await().until(() -> !driver.findElements(By.cssSelector("#slots button")).isEmpty());
            String journeyWindow = driver.getWindowHandle();
            element("flow-open").click();
            await().until(() -> driver.getWindowHandles().size() == 2);
            String flowWindow =
                    driver.getWindowHandles().stream()
                            .filter(handle -> !handle.equals(journeyWindow))
                            .findFirst()
                            .orElseThrow();
            driver.switchTo().window(journeyWindow);
            // Exercise the directory query and response decoding through every browser adapter.
            for (String protocol : List.of("GraphQL", "SOAP", "REST")) {
                select("protocol", protocol);
                press("refresh");
                assertThat(driver.findElements(By.cssSelector("#slots button"))).isNotEmpty();
                assertThat(feedback()).contains("compartments available");
            }
            assertFlow("SearchCompartments");
            assertThat(element("db-changes").getText()).contains("No committed changes");
            press("execute");
            assertThat(state()).isEqualTo("RESERVED");
            id = element("current-id").getText();
            UUID.fromString(id);
            String reserveTrace = trace();
            driver.switchTo().window(flowWindow);
            await().until(() -> reserveTrace.equals(trace()));
            assertFlow("Receipt stored");
            assertThat(element("flow-diagram").findElements(By.cssSelector(".edge.seen")))
                    .hasSizeGreaterThan(3);
            assertAcrossPages("changes", "INSERT", "booking", "outbox", "DELETE");
            new Select(element("flow-attempt")).selectByValue(reserveTrace);
            driver.switchTo().window(journeyWindow);
            String reservationRequest = firstDetail();
            press("retry");
            assertThat(firstDetail()).contains(keyIn(reservationRequest));
            assertThat(trace()).isNotEqualTo(reserveTrace);
            assertFlow("Command replayed");
            assertThat(element("db-changes").getText()).contains("No committed changes");
            assertThat(element("current-id").getText()).isEqualTo(id);
            press("execute"); // Customer attempts the SOAP deposit.
            assertThat(feedback()).contains("PERMISSION_DENIED");
            assertFlow("Request failed");
            await().untilAsserted(
                            () ->
                                    assertThat(element("db-changes").getText())
                                            .contains("No committed changes"));
            role("courier");
            press("execute");
            assertThat(state()).isEqualTo("DEPOSITED");
            String depositTrace = trace();
            driver.switchTo().window(flowWindow);
            assertThat(trace()).isEqualTo(reserveTrace);
            new Select(element("flow-attempt")).selectByValue("");
            await().until(() -> depositTrace.equals(trace()));
            assertFlow("Email sent");
            assertFlow("Receipt stored");
            assertAcrossPages("changes", "DEPOSITED", "outbox");
            new Select(element("db-table")).selectByValue("compartment");
            assertAcrossPages("rows", "Central", "SMALL");
            driver.switchTo().window(journeyWindow);
            String oldCode = LockerJourneyIT.pickupMail(id, null);
            role("customer");
            new Select(element("operation")).selectByValue("reissue");
            select("protocol", "GraphQL");
            press("execute");
            String code = LockerJourneyIT.pickupMail(id, oldCode);
            String wrong = (code.startsWith("0") ? "1" : "0") + code.substring(1);
            element("pickup").sendKeys(wrong);
            press("execute");
            assertThat(feedback()).contains("FAILED_PRECONDITION");
            element("pickup").clear();
            element("pickup").sendKeys(code);
            press("execute");
            assertThat(state()).isEqualTo("COLLECTED");
            assertThat(element("view-logs").getAttribute("href")).endsWith("var-reservation=" + id);
            assertFlow("Receipt stored");
            assertAcrossPages("changes", "COLLECTED");
            for (String resource :
                    List.of(
                            "tables/booking",
                            "tables/outbox",
                            "tables/command_result",
                            "changes?trace=" + depositTrace,
                            "traces/" + depositTrace)) {
                var response =
                        send("GET", LAB + "/inspect/" + resource, null, null, null, Map.of());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body())
                        .doesNotContain(
                                "verifier",
                                "envelope",
                                "owner_sub",
                                "@example.test",
                                "\"email\"",
                                "\"payload\"",
                                "fingerprint",
                                "Bearer ",
                                oldCode,
                                code,
                                wrong);
            }
            String history = element("entries").getDomProperty("textContent");
            assertThat(history.contains(code) || history.contains(wrong))
                    .as("pickup codes masked in history")
                    .isFalse();
            assertThat(history)
                    .contains("[masked]", "REST · reserve", "SOAP · deposit", "GraphQL · collect");

            new Select(element("operation")).selectByValue("get");
            select("protocol", "SOAP");
            press("execute");
            assertThat(state()).isEqualTo("COLLECTED");
            // A trace-store outage must leave the guided journey and database inspector usable.
            ((JavascriptExecutor) driver)
                    .executeScript(
                            """
                            window.liveFetch = window.fetch;
                            window.fetch = (path, options) => String(path).startsWith('/inspect/traces/')
                                ? Promise.resolve(new Response('', {status: 503})) : window.liveFetch(path, options);
                            """);
            try {
                press("execute");
                assertThat(state()).isEqualTo("COLLECTED");
                await().untilAsserted(
                                () ->
                                        assertThat(element("trace-status").getText())
                                                .contains("unavailable"));
                assertThat(element("rows-status").getText()).contains("Updated");
            } finally {
                ((JavascriptExecutor) driver)
                        .executeScript("window.fetch = window.liveFetch; delete window.liveFetch;");
            }
            assertFlow("gRPC · Get");
            String reservation = id;
            await().ignoreExceptions()
                    .atMost(Duration.ofSeconds(70))
                    .untilAsserted(
                            () -> {
                                var lines = logs(reservation);
                                var services = new HashSet<String>();
                                var outcomes = new HashSet<String>();
                                for (var stream : lines) {
                                    services.add(stream.path("stream").path("service").asString());
                                    for (var pair : stream.path("values"))
                                        outcomes.add(
                                                JSON.readTree(pair.get(1).asString())
                                                        .path("outcome")
                                                        .asString());
                                }
                                assertThat(services)
                                        .contains(
                                                "rest",
                                                "graphql",
                                                "soap",
                                                "reservations",
                                                "notifications",
                                                "receipts");
                                assertThat(outcomes)
                                        .contains(
                                                "committed",
                                                "replayed",
                                                "sent",
                                                "stored",
                                                "PERMISSION_DENIED",
                                                "FAILED_PRECONDITION");
                                String raw = lines.toString();
                                boolean leaked =
                                        List.of(
                                                        code,
                                                        wrong,
                                                        "lab-customer@example.test",
                                                        "Bearer ",
                                                        "envelope",
                                                        "pickup.key")
                                                .stream()
                                                .anyMatch(raw::contains);
                                assertThat(leaked)
                                        .as("sensitive fields excluded from reservation events")
                                        .isFalse();
                                assertThat(sample(counter)).isGreaterThan(initial);
                                assertThat(query("locker_outbox_pending").size()).isOne();
                                assertThat(
                                                query(
                                                                "kafka_consumergroup_lag{consumergroup=~\"locker-.*\"}")
                                                        .size())
                                        .isGreaterThanOrEqualTo(2);
                                for (var series : query("{__name__=~\"locker_.*\"}")) {
                                    assertThat(series.path("metric").propertyNames())
                                            .doesNotContain(
                                                    "reservation_id",
                                                    "event_id",
                                                    "request_id",
                                                    "email",
                                                    "code");
                                }
                            });
            String logUrl = element("view-logs").getAttribute("href");
            new Select(element("operation")).selectByValue("reserve");
            select("protocol", "GraphQL");
            press("refresh");
            press("execute");
            assertThat(state()).isEqualTo("RESERVED");
            id = element("current-id").getText();
            new Select(element("operation")).selectByValue("cancel");
            select("protocol", "SOAP");
            press("execute");
            assertThat(state()).isEqualTo("CANCELLED");
            driver.get(logUrl);
            await().atMost(Duration.ofSeconds(30))
                    .until(
                            () ->
                                    driver.findElement(By.tagName("body"))
                                            .getText()
                                            .contains("Reservation events"));
            var panel =
                    driver.findElement(
                            By.xpath("//*[normalize-space(text())='Reservation events']"));
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].scrollIntoView({block:'center'})", panel);
            await().atMost(Duration.ofSeconds(30))
                    .until(
                            () ->
                                    driver.findElement(By.tagName("body"))
                                            .getText()
                                            .contains(reservation));
        } finally {
            driver.quit();
            if (id != null) releaseFixture(id);
        }
    }

    private static void releaseFixture(String id) throws Exception {
        String customer = token("lab-customer", "locker.read locker.reserve");
        var reservation = call(Protocol.REST, "get", customer, id, "", "");
        if ("RESERVED".equals(reservation.state()))
            call(Protocol.REST, "cancel", customer, id, UUID.randomUUID().toString(), "");
        if ("DEPOSITED".equals(reservation.state())) {
            String old = LockerJourneyIT.pickupMail(id, null);
            call(Protocol.REST, "reissue", customer, id, UUID.randomUUID().toString(), "");
            String fresh = LockerJourneyIT.pickupMail(id, old);
            assertThat(
                            call(
                                            Protocol.REST,
                                            "collect",
                                            customer,
                                            id,
                                            UUID.randomUUID().toString(),
                                            fresh)
                                    .state())
                    .isEqualTo("COLLECTED");
        }
    }

    private String trace() {
        return element("flow-panel").getDomAttribute("data-trace");
    }

    private void assertFlow(String operation) {
        await().atMost(Duration.ofSeconds(30))
                .ignoreExceptions()
                .untilAsserted(
                        () -> assertThat(element("flow-timeline").getText()).contains(operation));
    }

    private void assertAcrossPages(String panel, String... expected) {
        var seen = new StringBuilder();
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            seen.append('\n').append(element("db-" + panel).getText());
                            var text = seen.toString();
                            if (!Arrays.stream(expected).allMatch(text::contains)
                                    && element(panel + "-next").isEnabled()) {
                                element(panel + "-next").click();
                            }
                            assertThat(text).contains(expected);
                        });
    }

    private String keyIn(String detail) {
        return detail.lines()
                .filter(line -> line.startsWith("Idempotency-Key: "))
                .findFirst()
                .orElseThrow();
    }

    private WebElement element(String id) {
        return driver.findElement(By.id(id));
    }

    private void select(String id, String value) {
        new Select(element(id)).selectByVisibleText(value);
    }

    private void role(String value) {
        driver.findElement(By.cssSelector("input[value='" + value + "']")).click();
    }

    private String feedback() {
        return element("feedback").getText();
    }

    private String state() {
        return element("state").getText();
    }

    private String firstDetail() {
        return driver.findElement(By.cssSelector("#entries details:first-child pre"))
                .getDomProperty("textContent");
    }

    private void press(String id) {
        var before = driver.findElements(By.cssSelector("#entries details:first-child"));
        element(id).click();
        await().atMost(Duration.ofSeconds(15))
                .until(
                        () ->
                                !driver.findElements(By.cssSelector("#entries details:first-child"))
                                        .equals(before));
    }

    private static JsonNode data(String uid, String path, String expression) throws Exception {
        var result =
                send(
                        "GET",
                        GRAFANA
                                + "/api/datasources/proxy/uid/"
                                + uid
                                + path
                                + URLEncoder.encode(expression, StandardCharsets.UTF_8),
                        null,
                        null,
                        null,
                        Map.of());
        assertThat(result.statusCode()).isEqualTo(200);
        return JSON.readTree(result.body()).path("data").path("result");
    }

    private static JsonNode logs(String id) throws Exception {
        return data(
                "locker-loki",
                "/loki/api/v1/query_range?limit=300&query=",
                "{job=\"lockers\"} |= \"" + id + "\"");
    }

    private static JsonNode query(String expr) throws Exception {
        return data("locker-prom", "/api/v1/query?query=", expr);
    }

    private static double sample(String expression) throws Exception {
        var samples = query(expression);
        return samples.isEmpty()
                ? 0
                : Double.parseDouble(samples.get(0).get("value").get(1).asString());
    }
}
