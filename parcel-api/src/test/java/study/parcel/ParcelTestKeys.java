package study.parcel;

import java.nio.file.Files;
import java.security.*;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The application and exercise tests share this public verification key for this JVM run. */
abstract class ParcelTestKeys {
    static final KeyPair TRUSTED = newKey();

    static KeyPair newKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    @DynamicPropertySource
    static void trustedKey(DynamicPropertyRegistry properties) throws Exception {
        var pem = Files.createTempFile("parcel-test-public-", ".pem");
        pem.toFile().deleteOnExit();
        var body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(TRUSTED.getPublic().getEncoded());
        Files.writeString(pem, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        properties.add("spring.security.oauth2.resourceserver.jwt.public-key-location", () -> pem.toUri().toString());
    }
}
