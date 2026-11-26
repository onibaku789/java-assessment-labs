package study.parcel;

import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:week02",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class Week02SecurityIT extends ParcelTestKeys {
    static final String ISSUER = "urn:parcel:study";
    static final String AUDIENCE = "parcel-api";
    static final String ORIGIN = "http://127.0.0.1:18082";
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired
    MockMvc http;

    private static Map<String, Object> readerClaims() {
        long now = Instant.now().getEpochSecond();
        return new HashMap<>(Map.of("sub", "local-test", "iss", ISSUER, "aud", AUDIENCE,
                "scope", "parcel.read", "iat", now - 600, "exp", now + 300));
    }

    private static String bearer(KeyPair key, Map<String, Object> claims) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String payload = base64.encodeToString(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")))
                + "." + base64.encodeToString(JSON.writeValueAsBytes(claims));
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(payload.getBytes(US_ASCII));
        return "Bearer " + payload + "." + base64.encodeToString(signer.sign());
    }

    private ResultActions lookup(Map<String, Object> claims) throws Exception {
        return http.perform(get("/parcels/P-100").header("Authorization", bearer(TRUSTED, claims)));
    }

    private MockHttpServletRequestBuilder parcelRequest(String trackingId, String credential) throws Exception {
        var request = get("/parcels/{id}", trackingId);
        if (!"no token".equals(credential)) {
            var claims = readerClaims();
            claims.put("scope", credential);
            request.header("Authorization", bearer(TRUSTED, claims));
        }
        return request;
    }

    @Test
    void readerCanReadTheSeededParcel() throws Exception {
        lookup(readerClaims()).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("P-100"));
    }

    @Test
    void aMissingTokenReturns401() throws Exception {
        http.perform(get("/parcels/P-100")).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidObserverHasInsufficientScopeAndGets403() throws Exception {
        http.perform(parcelRequest("P-100", "parcel.observe")).andExpect(status().isForbidden());
    }

    @Test
    void rejectExpiredToken() throws Exception {
        var claims = readerClaims();
        claims.put("exp", Instant.now().minusSeconds(300).getEpochSecond());
        lookup(claims).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectWrongIssuer() throws Exception {
        var claims = readerClaims();
        claims.put("iss", "urn:other");
        lookup(claims).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectWrongAudience() throws Exception {
        var claims = readerClaims();
        claims.put("aud", "other-api");
        lookup(claims).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectTokenSignedByAnUntrustedKey() throws Exception {
        http.perform(get("/parcels/P-100").header("Authorization", bearer(newKey(), readerClaims())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerTokenReturns401() throws Exception {
        http.perform(get("/parcels/P-100").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "signed reader token missing {0} returns 401")
    @ValueSource(strings = {"iss", "aud", "exp"})
    void rejectMissingRequiredClaim(String claim) throws Exception {
        var claims = readerClaims();
        claims.remove(claim);
        var response = lookup(claims).andReturn().getResponse();
        assertEquals(401, response.getStatus(), "Signed token missing " + claim);
    }

    @Test
    void readerCanReadWhenParcelApiIsAmongSeveralAudiences() throws Exception {
        var claims = readerClaims();
        claims.put("aud", List.of("billing-api", AUDIENCE));
        lookup(claims).andExpect(status().isOk()).andExpect(jsonPath("$.trackingId").value("P-100"));
    }

    @Test
    void rejectAudienceListThatOmitsParcelApi() throws Exception {
        var claims = readerClaims();
        claims.put("aud", List.of("billing-api", "shipping-api"));
        lookup(claims).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithObserveAndReadScopesCanRead() throws Exception {
        http.perform(parcelRequest("P-100", "parcel.observe parcel.read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trackingId").value("P-100"));
    }

    @Test
    void authenticatedCallerWithoutAScopeGets403() throws Exception {
        var claims = readerClaims();
        claims.remove("scope");
        lookup(claims).andExpect(status().isForbidden());
    }

    @Test
    void similarlyNamedScopeDoesNotGrantReadPermission() throws Exception {
        http.perform(parcelRequest("P-100", "parcel.readonly")).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "unknown parcel with {0} returns {1}")
    @CsvSource({"no token, 401", "parcel.observe, 403", "parcel.read, 404"})
    void unknownParcelIsDisclosedOnlyToAReader(String credential, int expectedStatus) throws Exception {
        var response = http.perform(parcelRequest("P-MISSING", credential)).andReturn().getResponse();
        assertEquals(expectedStatus, response.getStatus(), "Unknown parcel with " + credential);
    }

    private ResultActions preflight(String origin) throws Exception {
        return http.perform(options("/parcels/P-100").header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"));
    }

    @Test
    void allowTheBrowserOriginToSendTheAuthorizationHeader() throws Exception {
        preflight(ORIGIN).andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("GET")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
    }

    @Test
    void anUnlistedOriginGetsNoCorsGrant() throws Exception {
        preflight("http://127.0.0.1:18099").andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest(name = "allowed origin can read the {1} response with {0}")
    @CsvSource({"parcel.read, 200", "no token, 401", "parcel.observe, 403"})
    void allowedBrowserOriginCanReadActualLookupResponse(String credential, int expectedStatus) throws Exception {
        var response = http.perform(parcelRequest("P-100", credential).header("Origin", ORIGIN)).andReturn().getResponse();
        assertAll("Browser lookup with " + credential,
                () -> assertEquals(expectedStatus, response.getStatus(), "HTTP status"),
                () -> assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"), "Access-Control-Allow-Origin"));
    }
}
