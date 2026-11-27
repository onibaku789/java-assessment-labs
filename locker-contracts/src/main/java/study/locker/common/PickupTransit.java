package study.locker.common;

import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClientResponseException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Transit owns key material. Historical records are translated into Vault's version-one wire format. */
public record PickupTransit(VaultOperations vault) {
    public enum MacKey {
        PICKUP("pickup-verifier"), COMMAND("command-fingerprint");

        private final String name;

        MacKey(String name) {
            this.name = name;
        }
    }

    public String fingerprint(MacKey key, String input) {
        return (String) write("hmac/" + key.name, Map.of("input", encode(input))).get("hmac");
    }

    public boolean verifies(MacKey key, String input, String stored) {
        boolean legacy = !stored.startsWith("vault:");
        String tag = legacy ? "vault:v1:" + Base64.getEncoder().encodeToString(HexFormat.of().parseHex(stored)) : stored;
        var result = write("verify/" + (legacy ? "legacy-mac" : key.name), Map.of("input", encode(input), "hmac", tag));
        return Boolean.TRUE.equals(result.get("valid"));
    }

    public String seal(String eventId, String code) {
        var body = Map.of("plaintext", encode(code), "associated_data", encode(eventId));
        return (String) write("encrypt/pickup-mail", body).get("ciphertext");
    }

    public String open(String eventId, String envelope) {
        boolean legacy = !envelope.startsWith("vault:");
        var body = Map.of("ciphertext", legacy ? "vault:v1:" + envelope : envelope, "associated_data", encode(eventId));
        String plaintext = (String) write("decrypt/" + (legacy ? "legacy-mail" : "pickup-mail"), body).get("plaintext");
        return new String(Base64.getDecoder().decode(plaintext), StandardCharsets.UTF_8);
    }

    private Map<String, Object> write(String path, Map<String, String> body) {
        try {
            return Objects.requireNonNull(vault.write("transit/" + path, body)).getRequiredData();
        } catch (VaultClientResponseException failure) {
            if (failure.getStatusCode().value() == 400 && path.startsWith("decrypt/")) {
                throw new IllegalArgumentException("Invalid pickup envelope");
            }
            throw new Unavailable();
        } catch (VaultException | RestClientException failure) {
            throw new Unavailable();
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable() {
            super("Pickup key service unavailable");
        }
    }
}
