package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static study.locker.verification.StackClient.*;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;

import java.time.Duration;
import java.util.*;

abstract class ManualBrowser {
    WebDriver driver;

    String trace() {
        return element("flow-panel").getDomAttribute("data-trace");
    }

    void assertFlow(String operation) {
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions()
                .untilAsserted(() -> assertThat(element("flow-timeline").getText()).contains(operation));
    }

    void assertAcrossPages(String panel, String... expected) {
        var seen = new StringBuilder();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            seen.append('\n').append(element("db-" + panel).getText());
            var text = seen.toString();
            if (!Arrays.stream(expected).allMatch(text::contains) && element(panel + "-next").isEnabled()) {
                element(panel + "-next").click();
            }
            assertThat(text).contains(expected);
        });
    }

    String keyIn(String detail) {
        return detail.lines().filter(line -> line.startsWith("Idempotency-Key: ")).findFirst().orElseThrow();
    }

    WebElement element(String id) {
        return driver.findElement(By.id(id));
    }

    void select(String id, String value) {
        new Select(element(id)).selectByVisibleText(value);
    }

    void role(String value) {
        driver.findElement(By.cssSelector("input[value='" + value + "']")).click();
    }

    String feedback() {
        return element("feedback").getText();
    }

    String state() {
        return element("state").getText();
    }

    String firstDetail() {
        return driver.findElement(By.cssSelector("#entries details:first-child pre")).getDomProperty("textContent");
    }

    void press(String id) {
        var before = driver.findElements(By.cssSelector("#entries details:first-child"));
        element(id).click();
        await().atMost(Duration.ofSeconds(15))
                .until(() -> !driver.findElements(By.cssSelector("#entries details:first-child")).equals(before));
    }

    static void releaseFixture(String id) throws Exception {
        String customer = token("lab-customer", "locker.read locker.reserve");
        var reservation = call(Protocol.REST, "get", customer, id, "", "");
        if ("RESERVED".equals(reservation.state()))
            call(Protocol.REST, "cancel", customer, id, UUID.randomUUID().toString(), "");
        if ("DEPOSITED".equals(reservation.state())) {
            String old = LockerJourneyIT.pickupMail(id, null);
            call(Protocol.REST, "reissue", customer, id, UUID.randomUUID().toString(), "");
            String fresh = LockerJourneyIT.pickupMail(id, old);
            assertThat(call(Protocol.REST, "collect", customer, id, UUID.randomUUID().toString(),
                    fresh).state()).isEqualTo("COLLECTED");
        }
    }

}
