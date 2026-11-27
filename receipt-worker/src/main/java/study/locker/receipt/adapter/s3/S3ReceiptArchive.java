package study.locker.receipt.adapter.s3;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import study.locker.receipt.application.StoreLifecycleReceipt;
import study.locker.receipt.domain.model.Receipt;

import tools.jackson.databind.ObjectMapper;

public record S3ReceiptArchive(S3Client objects, ObjectMapper json, String bucket)
        implements StoreLifecycleReceipt.Archive {

    private static String key(Receipt event) {
        return "receipts/" + event.reservationId() + "/" + event.eventId() + ".json";
    }

    public boolean createIfAbsent(Receipt event) {
        try {
            objects.putObject(
                    r ->
                            r.bucket(bucket)
                                    .key(key(event))
                                    .contentType("application/json")
                                    .ifNoneMatch("*"),
                    RequestBody.fromString(json.writeValueAsString(event)));
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 412) return false;
            throw ex;
        }
    }

    public Receipt existing(Receipt event) {
        return json.readValue(
                objects.getObjectAsBytes(r -> r.bucket(bucket).key(key(event))).asByteArray(),
                Receipt.class);
    }
}
