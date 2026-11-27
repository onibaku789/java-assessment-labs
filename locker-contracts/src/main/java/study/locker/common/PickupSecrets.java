package study.locker.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** A separate, stable lab secret from the JWT signing key. */
public final class PickupSecrets {
    private final byte[] master;
    private final SecureRandom random = new SecureRandom();

    public PickupSecrets(String base64Key) {
        master = Base64.getDecoder().decode(base64Key.trim());
        if (master.length != 32)
            throw new IllegalArgumentException("Expected a 256-bit pickup secret");
    }

    public String code() {
        return String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
    }

    public String fingerprint(String value) {
        return HexFormat.of().formatHex(mac(value));
    }

    public boolean matches(String booking, String code, String verifier) {
        return MessageDigest.isEqual(
                mac("pickup:" + booking + ":" + code), HexFormat.of().parseHex(verifier));
    }

    public String seal(String issueId, String code) {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        byte[] encrypted =
                crypt(Cipher.ENCRYPT_MODE, iv, issueId, code.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder()
                .encodeToString(
                        ByteBuffer.allocate(iv.length + encrypted.length)
                                .put(iv)
                                .put(encrypted)
                                .array());
    }

    public String open(String issueId, String envelope) {
        var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(envelope));
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return new String(
                crypt(Cipher.DECRYPT_MODE, iv, issueId, encrypted), StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, byte[] iv, String issueId, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    mode,
                    new SecretKeySpec(mac("locker-mail-key-v1"), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(issueId.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid pickup envelope", e);
        }
    }

    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
