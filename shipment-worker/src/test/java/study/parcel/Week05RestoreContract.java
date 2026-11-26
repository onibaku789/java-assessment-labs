package study.parcel;

import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import software.amazon.awssdk.core.sync.RequestBody;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class Week05RestoreContract {
    @Container final GenericContainer<?> store = LocalAws.objectsContainer();

    @Test void restoreTwoDatasetSizesThreeTimesAndPreserveAnExistingReceipt(CapturedOutput output)
            throws Exception {
        try (var objects = LocalAws.objects(store)) {
            objects.createBucket(r -> r.bucket(Worker.BUCKET));
            objects.putObject(r -> r.bucket(Worker.BUCKET).key("existing"), RequestBody.fromString("keep"));
            for (int size : List.of(64, 64 * 1024)) {
                for (int trial = 1; trial <= 3; trial++) {
                    byte[] data = new byte[size];
                    new Random(trial).nextBytes(data);
                    String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
                    int offset = output.getOut().length();
                    RestoreDrill.run(objects, data);
                    assertThat(output.getOut().substring(offset)).as("Compare backup bytes and record the measured restore")
                            .containsPattern("before=" + sha + " after=" + sha + " recovery_ms=\\d+ bytes=" + size);
                }
            }
            assertThat(objects.getObjectAsBytes(r -> r.bucket(Worker.BUCKET).key("existing")).asUtf8String())
                    .isEqualTo("keep");
            assertThat(objects.listObjectsV2(r -> r.bucket(Worker.BUCKET)).contents()).hasSize(1);
        }
    }
}
