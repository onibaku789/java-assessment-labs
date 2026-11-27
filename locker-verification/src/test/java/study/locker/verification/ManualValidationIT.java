package study.locker.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static study.locker.verification.StackClient.*;

@EnabledIfSystemProperty(named = "locker.manual", matches = "true")
class ManualValidationIT extends ManualBrowser {
    @Test
    void invalidDepositShowsTheSameApplicationCodeForAllThreeProtocols() throws Exception {
        startBrowser();
        try {
            driver.get(LAB);
            await().until(() -> !driver.findElements(By.cssSelector("#entries details")).isEmpty());
            role("courier");
            select("operation", "Deposit parcel");
            element("booking").sendKeys("yxc");
            String rest = depositError("REST", 400);
            assertThat(JSON.readTree(rest).path("message").asString()).isEqualTo("Expected UUID");
            String graphql = depositError("GraphQL", 400);
            assertThat(JSON.readTree(graphql).path("errors").get(0).path("extensions").path("classification").asString())
                    .isEqualTo("ValidationError");
            String soap = depositError("SOAP", 500);
            assertThat(xml(soap).getElementsByTagName("faultcode").item(0).getTextContent()).endsWith(":Client");
        } finally {
            driver.quit();
        }
    }

    private String depositError(String protocol, int status) {
        select("protocol", protocol);
        press("execute");
        String title = driver.findElement(By.cssSelector("#entries details:first-child summary")).getText();
        assertThat(title).contains(protocol + " · deposit", "HTTP " + status, "INVALID_ARGUMENT");
        assertThat(feedback()).contains("INVALID_ARGUMENT");
        String detail = firstDetail();
        assertThat(detail).contains("yxc", "Response\n");
        return detail.split("Response\n", 2)[1];
    }

    @Test
    void helperStillRejectsUnknownOperations() throws Exception {
        var response = send("POST", LAB + "/gateway/rest/v1/reservations/yxc/unknown", null, "application/json",
                "", Map.of("X-Lab-Role", "courier"));
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(response.body()).path("message").asString()).isEqualTo("Unknown operation");
    }
}
