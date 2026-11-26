package study.parcel;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

final class RestoreDrill {
    static void run(S3Client objects, byte[] fixture) throws Exception {
        String key = "restore-drills/" + UUID.randomUUID() + ".json";
        String bucket = Worker.BUCKET;
        var backup = Files.createTempFile("parcel-recovery-", ".json");
        try {
            objects.putObject(r -> r.bucket(bucket).key(key), RequestBody.fromBytes(fixture));
            byte[] saved = objects.getObjectAsBytes(r -> r.bucket(bucket).key(key)).asByteArray();
            Files.write(backup, saved);
            objects.deleteObject(r -> r.bucket(bucket).key(key));
            if (!objects.listObjectsV2(r -> r.bucket(bucket).prefix(key)).contents().isEmpty()) {
                throw new IllegalStateException("Fixture still visible after deletion");
            }
            long started = System.nanoTime();
            objects.putObject(r -> r.bucket(bucket).key(key), RequestBody.fromFile(backup));
            byte[] restored = objects.getObjectAsBytes(r -> r.bucket(bucket).key(key)).asByteArray();
            long millis = (System.nanoTime() - started) / 1_000_000;
            if (!Arrays.equals(fixture, restored)) throw new IllegalStateException("Recovery mismatch");
            var sha = MessageDigest.getInstance("SHA-256");
            System.out.printf("before=%s after=%s recovery_ms=%d bytes=%d%n",
                HexFormat.of().formatHex(sha.digest(fixture)), HexFormat.of().formatHex(sha.digest(restored)),
                millis, restored.length);
        } finally {
            try {
                objects.deleteObject(r -> r.bucket(bucket).key(key));
            } finally {
                Files.deleteIfExists(backup);
            }
        }
    }
}
