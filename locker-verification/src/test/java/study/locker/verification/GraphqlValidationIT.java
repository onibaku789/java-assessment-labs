package study.locker.verification;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.verification.StackClient.*;

@EnabledIfSystemProperty(named = "locker.stack", matches = "true")
class GraphqlValidationIT {
    @ParameterizedTest
    @CsvSource({"application/graphql-response+json,400", "application/json,200"})
    void validationErrorsRetainDetailsWithTheNegotiatedStatus(String accept, int status) throws Exception {
        var body = JSON.writeValueAsString(Map.of("query", "query($id:UUID!){reservation(id:$id){id}}",
                "variables", Map.of("id", "yxc")));
        var response = send("POST", local(18082) + "/graphql", token("reader", "locker.read"), "application/json",
                body, Map.of("Accept", accept));
        assertThat(response.statusCode()).isEqualTo(status);
        var result = JSON.readTree(response.body());
        assertThat(result.has("data")).isFalse();
        var error = result.path("errors").get(0);
        assertThat(error.path("message").asString()).contains("UUID");
        assertThat(error.path("locations").size()).isPositive();
        assertThat(error.path("extensions").path("classification").asString()).isEqualTo("ValidationError");
        assertThat(error.path("extensions").path("code").asString()).isEqualTo("INVALID_ARGUMENT");
    }
}
