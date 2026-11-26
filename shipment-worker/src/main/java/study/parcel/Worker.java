package study.parcel;

import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

public class Worker {
    static final String BUCKET = "parcel-study-receipts";
    static final String QUEUE = "parcel-study-events";
    static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Expected init, send, once, list, read or restore");
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        try (var queue = SqsClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials)
                    .endpointOverride(URI.create("http://127.0.0.1:9324")).build();
             var objects = S3Client.builder().region(Region.US_EAST_1).credentialsProvider(credentials)
                    .forcePathStyle(true).endpointOverride(URI.create("http://127.0.0.1:19090")).build()) {
            switch (args[0]) {
                case "init" -> {
                    queue.createQueue(q -> q.queueName(QUEUE));
                    if (objects.listBuckets().buckets().stream().noneMatch(b -> b.name().equals(BUCKET))) {
                        objects.createBucket(b -> b.bucket(BUCKET));
                    }
                    System.out.println("Local queue and bucket ready");
                }
                case "send" -> {
                    String body = Files.readString(Path.of(args[1]));
                    JSON.readValue(body, Shipment.class);
                    queue.sendMessage(q -> q.queueUrl(url(queue)).messageBody(body));
                    System.out.println("Sent " + body.trim());
                }
                case "once" -> consume(queue, objects, args.length == 2 && args[1].equals("--interrupt"));
                case "restore" -> RestoreDrill.run(objects, Files.readAllBytes(Path.of(args[1])));
                case "list" -> objects.listObjectsV2Paginator(r -> r.bucket(BUCKET)).contents()
                        .forEach(o -> System.out.println(o.key()));
                case "read" -> System.out.println(objects.getObjectAsBytes(r -> r.bucket(BUCKET)
                        .key(args[1])).asUtf8String());
                default -> throw new IllegalArgumentException("Unknown action: " + args[0]);
            }
        }
    }

    static String url(SqsClient queue) {
        return queue.getQueueUrl(r -> r.queueName(QUEUE)).queueUrl();
    }

    static void consume(SqsClient queue, S3Client objects, boolean interrupt) throws Exception {
        String url = url(queue);
        var batch = queue.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(5)
                .maxNumberOfMessages(1).waitTimeSeconds(2)).messages();
        for (var message : batch) {
            var event = JSON.readValue(message.body(), Shipment.class);
            // The baseline creates a receipt for each delivery. Week 4 changes this contract.
            String key = "receipts/" + event.eventId() + "/" + UUID.randomUUID() + ".json";
            objects.putObject(r -> r.bucket(BUCKET).key(key), RequestBody.fromString(message.body()));
            System.out.println("Persisted " + key);
            if (interrupt) throw new IllegalStateException("Exercise: interrupted before acknowledgment");
            queue.deleteMessage(r -> r.queueUrl(url).receiptHandle(message.receiptHandle()));
            System.out.println("Acknowledged " + event.eventId());
        }
        if (batch.isEmpty()) System.out.println("Queue empty");
    }
}
