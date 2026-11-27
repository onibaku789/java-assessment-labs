package study.locker.verification;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** Back up one reservation after its final lifecycle event has been delivered. */
public final class ReceiptArchive {
    private static final String BUCKET =
            System.getenv().getOrDefault("LOCKER_RECEIPTS_BUCKET", "locker-audit");
    private static final Pattern RECEIPT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json");

    private ReceiptArchive() {}

    static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("backup", "restore").contains(args[0]))
            throw new IllegalArgumentException(
                    "Expected backup|restore RESERVATION_UUID ARCHIVE_DIRECTORY");
        String prefix = "receipts/" + UUID.fromString(args[1]) + "/";
        var endpoint = System.getenv().getOrDefault("LOCKER_S3_ENDPOINT", "http://127.0.0.1:19090");
        try (var storage =
                S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        .forcePathStyle(true)
                        .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                        .build()) {
            var folder = Path.of(args[2]);
            if (args[0].equals("backup")) {
                backup(storage, prefix, folder);
            } else {
                if (!prefix.equals(Files.readString(folder.resolve("source-prefix")))) {
                    throw new IOException("Archive belongs to a different reservation");
                }
                restore(storage, prefix, folder);
            }
        }
    }

    static void backup(S3Client storage, String prefix, Path folder) throws IOException {
        Files.createDirectory(folder);
        List<String> manifest = new ArrayList<>();
        for (var item :
                storage.listObjectsV2Paginator(r -> r.bucket(BUCKET).prefix(prefix)).contents()) {
            String name = item.key().substring(prefix.length());
            if (!RECEIPT.matcher(name).matches())
                throw new IOException("Unexpected receipt filename");
            var bytes =
                    storage.getObjectAsBytes(r -> r.bucket(BUCKET).key(item.key())).asByteArray();
            Files.write(folder.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            manifest.add(digest(bytes) + "  " + name);
        }
        if (manifest.isEmpty()) throw new IOException("No receipts at " + prefix);
        Files.writeString(folder.resolve("source-prefix"), prefix, StandardOpenOption.CREATE_NEW);
        Files.write(folder.resolve("SHA256SUMS"), manifest, StandardOpenOption.CREATE_NEW);
    }

    static void restore(S3Client storage, String prefix, Path folder) throws IOException {
        var manifest = Files.readAllLines(folder.resolve("SHA256SUMS"));
        if (manifest.isEmpty()) throw new IOException("Empty receipt manifest");
        var verified = new LinkedHashMap<String, byte[]>();
        for (String line : manifest) {
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !RECEIPT.matcher(parts[1]).matches())
                throw new IOException("Invalid receipt manifest");
            byte[] bytes = Files.readAllBytes(folder.resolve(parts[1]));
            if (!digest(bytes).equals(parts[0]) || verified.putIfAbsent(parts[1], bytes) != null)
                throw new IOException("Invalid receipt checksum or repeated filename");
        }
        for (var record : verified.entrySet()) {
            String key = prefix + record.getKey();
            try {
                storage.putObject(
                        r ->
                                r.bucket(BUCKET)
                                        .key(key)
                                        .ifNoneMatch("*")
                                        .contentType("application/json"),
                        RequestBody.fromBytes(record.getValue()));
            } catch (S3Exception failure) {
                if (failure.statusCode() != 412) throw failure;
                var existing =
                        storage.getObjectAsBytes(r -> r.bucket(BUCKET).key(key)).asByteArray();
                if (!Arrays.equals(existing, record.getValue()))
                    throw new IOException("Existing receipt differs: " + key);
            }
        }
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
