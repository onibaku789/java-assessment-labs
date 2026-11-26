package study.parcel;

import java.nio.file.Files;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.US_ASCII;

/** The application and exercise tests share this public verification key for this JVM run. */
abstract class ParcelTestKeys {
    static final KeyPair TRUSTED = newKey();
    static final String AUDIENCE = "parcel-api";
    private static final ObjectMapper JSON = new ObjectMapper();

    static KeyPair newKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    static Map<String, Object> readerClaims() {
        long now = Instant.now().getEpochSecond();
        return new HashMap<>(Map.of("sub", "local-test", "iss", "urn:parcel:study", "aud", AUDIENCE,
                "scope", "parcel.read", "iat", now - 600, "exp", now + 300));
    }

    static String bearer(KeyPair key, Map<String, Object> claims) throws GeneralSecurityException {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String payload = base64.encodeToString(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")))
                + "." + base64.encodeToString(JSON.writeValueAsBytes(claims));
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(payload.getBytes(US_ASCII));
        return "Bearer " + payload + "." + base64.encodeToString(signer.sign());
    }

    @DynamicPropertySource
    static void trustedKey(DynamicPropertyRegistry properties) throws Exception {
        var pem = Files.createTempFile("parcel-test-public-", ".pem");
        pem.toFile().deleteOnExit();
        var body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(TRUSTED.getPublic().getEncoded());
        Files.writeString(pem, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        properties.add("spring.security.oauth2.resourceserver.jwt.public-key-location", () -> pem.toUri().toString());
    }
}
