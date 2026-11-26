package study.parcel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:week02",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class Week02SecurityIT extends ParcelTestKeys {
    static final String ORIGIN = "http://127.0.0.1:18082";
    @Autowired
    MockMvcTester http;

    private MvcTestResult lookup(Map<String, Object> claims) throws Exception {
        return http.get().uri("/parcels/P-100").header("Authorization", bearer(TRUSTED, claims)).exchange();
    }

    private MockMvcTester.MockMvcRequestBuilder parcelRequest(String trackingId, String credential) throws Exception {
        var request = http.get().uri("/parcels/{id}", trackingId);
        if (!"no token".equals(credential)) {
            var claims = readerClaims();
            claims.put("scope", credential);
            request.header("Authorization", bearer(TRUSTED, claims));
        }
        return request;
    }

    @Test
    void readerCanReadTheSeededParcel() throws Exception {
        assertThat(lookup(readerClaims())).hasStatusOk()
                .bodyJson().extractingPath("$.trackingId").isEqualTo("P-100");
    }

    @Test
    void aMissingTokenReturns401() {
        assertThat(http.get().uri("/parcels/P-100")).hasStatus(401);
    }

    @Test
    void aValidObserverHasInsufficientScopeAndGets403() throws Exception {
        assertThat(parcelRequest("P-100", "parcel.observe")).hasStatus(403);
    }

    @Test
    void rejectExpiredToken() throws Exception {
        var claims = readerClaims();
        claims.put("exp", Instant.now().minusSeconds(300).getEpochSecond());
        assertThat(lookup(claims)).hasStatus(401);
    }

    @Test
    void rejectWrongIssuer() throws Exception {
        var claims = readerClaims();
        claims.put("iss", "urn:other");
        assertThat(lookup(claims)).hasStatus(401);
    }

    @Test
    void rejectWrongAudience() throws Exception {
        var claims = readerClaims();
        claims.put("aud", "other-api");
        assertThat(lookup(claims)).hasStatus(401);
    }

    @Test
    void rejectTokenSignedByAnUntrustedKey() throws Exception {
        var token = bearer(newKey(), readerClaims());
        assertThat(http.get().uri("/parcels/P-100").header("Authorization", token)).hasStatus(401);
    }

    @Test
    void malformedBearerTokenReturns401() {
        assertThat(http.get().uri("/parcels/P-100").header("Authorization", "Bearer malformed")).hasStatus(401);
    }

    @ParameterizedTest(name = "signed reader token missing {0} returns 401")
    @ValueSource(strings = {"iss", "aud", "exp"})
    void rejectMissingRequiredClaim(String claim) throws Exception {
        var claims = readerClaims();
        claims.remove(claim);
        assertThat(lookup(claims)).as("Signed token missing %s", claim).hasStatus(401);
    }

    @Test
    void readerCanReadWhenParcelApiIsAmongSeveralAudiences() throws Exception {
        var claims = readerClaims();
        claims.put("aud", List.of("billing-api", AUDIENCE));
        assertThat(lookup(claims)).hasStatusOk()
                .bodyJson().extractingPath("$.trackingId").isEqualTo("P-100");
    }

    @Test
    void rejectAudienceListThatOmitsParcelApi() throws Exception {
        var claims = readerClaims();
        claims.put("aud", List.of("billing-api", "shipping-api"));
        assertThat(lookup(claims)).hasStatus(401);
    }

    @Test
    void tokenWithObserveAndReadScopesCanRead() throws Exception {
        assertThat(parcelRequest("P-100", "parcel.observe parcel.read")).hasStatusOk()
                .bodyJson().extractingPath("$.trackingId").isEqualTo("P-100");
    }

    @Test
    void authenticatedCallerWithoutAScopeGets403() throws Exception {
        var claims = readerClaims();
        claims.remove("scope");
        assertThat(lookup(claims)).hasStatus(403);
    }

    @Test
    void similarlyNamedScopeDoesNotGrantReadPermission() throws Exception {
        assertThat(parcelRequest("P-100", "parcel.readonly")).hasStatus(403);
    }

    @ParameterizedTest(name = "unknown parcel with {0} returns {1}")
    @CsvSource({"no token, 401", "parcel.observe, 403", "parcel.read, 404"})
    void unknownParcelIsDisclosedOnlyToAReader(String credential, int expectedStatus) throws Exception {
        assertThat(parcelRequest("P-MISSING", credential))
                .as("Unknown parcel with %s", credential).hasStatus(expectedStatus);
    }

    private MvcTestResult preflight(String origin) {
        return http.options().uri("/parcels/P-100").header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization").exchange();
    }

    @Test
    void allowTheBrowserOriginToSendTheAuthorizationHeader() {
        var result = preflight(ORIGIN);
        assertThat(result).hasStatus2xxSuccessful().hasHeader("Access-Control-Allow-Origin", ORIGIN);
        var response = result.getResponse();
        assertThat(response.getHeader("Access-Control-Allow-Methods")).containsIgnoringCase("GET");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("authorization");
    }

    @Test
    void anUnlistedOriginGetsNoCorsGrant() {
        assertThat(preflight("http://127.0.0.1:18099")).doesNotContainHeader("Access-Control-Allow-Origin");
    }

    @ParameterizedTest(name = "allowed origin can read the {1} response with {0}")
    @CsvSource({"parcel.read, 200", "no token, 401", "parcel.observe, 403"})
    void allowedBrowserOriginCanReadActualLookupResponse(String credential, int expectedStatus) throws Exception {
        var response = parcelRequest("P-100", credential).header("Origin", ORIGIN).exchange().getResponse();
        assertSoftly(softly -> {
            softly.assertThat(response.getStatus()).as("HTTP status for %s", credential).isEqualTo(expectedStatus);
            softly.assertThat(response.getHeader("Access-Control-Allow-Origin"))
                    .as("Access-Control-Allow-Origin for %s", credential).isEqualTo(ORIGIN);
        });
    }
}
