package study.parcel;

import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:week02",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
@Disabled("Enable when starting Week 2")
class Week02SecurityIT extends ParcelTestKeys {
    static final String ISSUER = "urn:parcel:study";
    static final String AUDIENCE = "parcel-api";
    static final String ORIGIN = "http://127.0.0.1:18082";
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc http;

    private ResultActions lookup(KeyPair key, String scope, String issuer, String audience, long validFor)
            throws Exception {
        long now = Instant.now().getEpochSecond();
        var base64 = Base64.getUrlEncoder().withoutPadding();
        var claims = Map.of("sub", "local-test", "iss", issuer, "aud", audience,
                "scope", scope, "iat", now - 600, "exp", now + validFor);
        String payload = base64.encodeToString(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")))
                + "." + base64.encodeToString(JSON.writeValueAsBytes(claims));
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(payload.getBytes(US_ASCII));
        String token = payload + "." + base64.encodeToString(signer.sign());
        return http.perform(get("/parcels/P-100").header("Authorization", "Bearer " + token));
    }

    @Test void readerCanReadTheSeededParcel() throws Exception {
        lookup(TRUSTED, "parcel.read", ISSUER, AUDIENCE, 300).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("P-100"));
    }

    @Test void aMissingTokenReturns401() throws Exception {
        http.perform(get("/parcels/P-100")).andExpect(status().isUnauthorized());
    }

    @Test void aValidObserverHasInsufficientScopeAndGets403() throws Exception {
        lookup(TRUSTED, "parcel.observe", ISSUER, AUDIENCE, 300).andExpect(status().isForbidden());
    }
    @Test void rejectExpiredToken() throws Exception {
        lookup(TRUSTED, "parcel.read", ISSUER, AUDIENCE, -300).andExpect(status().isUnauthorized());
    }
    @Test void rejectWrongIssuer() throws Exception {
        lookup(TRUSTED, "parcel.read", "urn:other", AUDIENCE, 300).andExpect(status().isUnauthorized());
    }
    @Test void rejectWrongAudience() throws Exception {
        lookup(TRUSTED, "parcel.read", ISSUER, "other-api", 300).andExpect(status().isUnauthorized());
    }
    @Test void rejectTokenSignedByAnUntrustedKey() throws Exception {
        lookup(newKey(), "parcel.read", ISSUER, AUDIENCE, 300).andExpect(status().isUnauthorized());
    }

    private ResultActions preflight(String origin) throws Exception {
        return http.perform(options("/parcels/P-100").header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"));
    }
    @Test void allowTheBrowserOriginToSendTheAuthorizationHeader() throws Exception {
        preflight(ORIGIN).andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("GET")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
    }
    @Test void anUnlistedOriginGetsNoCorsGrant() throws Exception {
        preflight("http://127.0.0.1:18099").andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
