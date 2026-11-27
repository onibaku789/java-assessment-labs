package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import static study.locker.verification.StackClient.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;

import java.time.Duration;
import java.util.*;

@EnabledIfSystemProperty(named = "locker.manual", matches = "true")
class ManualLabIT extends ManualBrowser {
    private String id;

    @Test
    void browserReceivesOnlyPublicSettings() throws Exception {
        var response = send("GET", LAB + "/inspect/config", null, null, null, Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        var config = JSON.readTree(response.body());
        assertThat(config.propertyNames()).containsExactlyInAnyOrder("refreshMs", "requestTimeoutMs", "historyLimit",
                "retentionHours", "grafanaUrl", "mailpitUrl");
        assertThat(config.path("historyLimit").asInt()).isPositive();
        assertThat(config.path("retentionHours").asInt()).isGreaterThanOrEqualTo(24);
    }

    @Test
    void guidedPickupIsVisibleInLogsAndMetrics() throws Exception {
        String counter = "sum(locker_gateway_requests_seconds_count{protocol=\"graphql\",operation=\"collect\",outcome=\"OK\"})";
        // A persisted Prometheus store can still hold the previous gateway's counter after a
        // restart.
        long started = java.time.Instant.now().getEpochSecond();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> LabTelemetry.sample("timestamp(up{service=\"graphql\"} == 1)") > started);
        double initial = LabTelemetry.sample(counter);
        startBrowser();

        try {
            String journeyWindow = driver.getWindowHandle();
            String flowWindow = openFlow(journeyWindow);
            checkDirectory();
            String reserveTrace = reserveAndReplay(journeyWindow, flowWindow);
            String depositTrace = deposit(journeyWindow, flowWindow, reserveTrace);
            var codes = collect();
            assertInspectionPrivacy(depositTrace, codes);
            checkTraceOutage();
            LabTelemetry.assertPickup(id, codes.current(), codes.wrong(), counter, initial);
            cancelAndCheckDashboard(id);
        } finally {
            driver.quit();
            if (id != null) releaseFixture(id);
        }
    }

    private String openFlow(String journeyWindow) {
        driver.get(LAB);
        await().until(() -> !driver.findElements(By.cssSelector("#slots button")).isEmpty());

        element("flow-open").click();
        await().until(() -> driver.getWindowHandles().size() == 2);
        String flowWindow = driver.getWindowHandles().stream().filter(handle -> !handle.equals(journeyWindow))
                .findFirst().orElseThrow();
        driver.switchTo().window(journeyWindow);
        return flowWindow;
    }

    private void checkDirectory() {
        // Exercise the directory query and response decoding through every browser adapter.
        for (String protocol : List.of("GraphQL", "SOAP", "REST")) {
            select("protocol", protocol);
            press("refresh");
            assertThat(driver.findElements(By.cssSelector("#slots button"))).isNotEmpty();
            assertThat(feedback()).contains("compartments available");
        }
        assertFlow("SearchCompartments");
        assertThat(element("db-changes").getText()).contains("No committed changes");
    }

    private String reserveAndReplay(String journeyWindow, String flowWindow) {
        press("execute");
        assertThat(state()).isEqualTo("RESERVED");
        id = element("current-id").getText();
        UUID.fromString(id);
        String reserveTrace = trace();
        driver.switchTo().window(flowWindow);
        await().until(() -> reserveTrace.equals(trace()));
        assertFlow("Receipt stored");
        assertThat(element("flow-diagram").findElements(By.cssSelector(".edge.seen"))).hasSizeGreaterThan(3);
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
        return reserveTrace;
    }

    private String deposit(String journeyWindow, String flowWindow, String reserveTrace) {
        press("execute"); // Customer attempts the SOAP deposit.
        assertThat(feedback()).contains("PERMISSION_DENIED");
        assertFlow("Request failed");
        await().untilAsserted(() -> assertThat(element("db-changes").getText()).contains("No committed changes"));
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
        return depositTrace;
    }

    private record PickupCodes(String previous, String current, String wrong) {
    }

    private PickupCodes collect() throws Exception {
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
        return new PickupCodes(oldCode, code, wrong);
    }

    private void assertInspectionPrivacy(String depositTrace, PickupCodes codes) throws Exception {
        for (String resource : List.of("tables/booking", "tables/outbox", "tables/command_result",
                "changes?trace=" + depositTrace, "traces/" + depositTrace)) {
            var response = send("GET", LAB + "/inspect/" + resource, null, null, null, Map.of());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).doesNotContain("verifier", "envelope", "owner_sub", "@example.test",
                    "\"email\"", "\"payload\"", "fingerprint", "Bearer ", codes.previous(), codes.current(),
                    codes.wrong());
        }
        String history = element("entries").getDomProperty("textContent");
        assertThat(history.contains(codes.current()) || history.contains(codes.wrong())).as(
                "pickup codes masked in history").isFalse();
        assertThat(history).contains("[masked]", "REST · reserve", "SOAP · deposit", "GraphQL · collect");

    }

    private void checkTraceOutage() {
        new Select(element("operation")).selectByValue("get");
        select("protocol", "SOAP");
        press("execute");
        assertThat(state()).isEqualTo("COLLECTED");
        // A trace-store outage must leave the guided journey and database inspector usable.
        ((JavascriptExecutor) driver).executeScript("""
                window.liveFetch = window.fetch;
                window.fetch = (path, options) => String(path).startsWith('/inspect/traces/')
                    ? Promise.resolve(new Response('', {status: 503})) : window.liveFetch(path, options);
                """);
        try {
            press("execute");
            assertThat(state()).isEqualTo("COLLECTED");
            await().untilAsserted(() -> assertThat(element("trace-status").getText()).contains("unavailable"));
            assertThat(element("rows-status").getText()).contains("Updated");
        } finally {
            ((JavascriptExecutor) driver).executeScript("window.fetch = window.liveFetch; delete window.liveFetch;");
        }
        assertFlow("gRPC · Get");
    }

    private void cancelAndCheckDashboard(String reservation) {
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
                .until(() -> driver.findElement(By.tagName("body")).getText().contains("Reservation events"));
        var panel = driver.findElement(By.xpath("//*[normalize-space(text())='Reservation events']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'})", panel);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> driver.findElement(By.tagName("body")).getText().contains(reservation));
    }
}
