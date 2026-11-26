package study.parcel;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@Disabled("Enable when starting Week 4")
class Week04DeliveryIT {
    // Fresh emulators for each test isolate the worker's fixed bucket and queue names.
    @Container final GenericContainer<?> store = LocalAws.objectsContainer();
    @Container final GenericContainer<?> broker = LocalAws.queueContainer();
    S3Client objects;
    SqsClient queue;
    String url;

    @BeforeEach void connect() {
        objects = LocalAws.objects(store);
        queue = LocalAws.queue(broker);
        url = queue.createQueue(r -> r.queueName(Worker.QUEUE)).queueUrl();
        objects.createBucket(r -> r.bucket(Worker.BUCKET));
    }

    @AfterEach void disconnect() {
        if (queue != null) queue.close();
        if (objects != null) objects.close();
    }

    private void send(String id) {
        String body = Worker.JSON.writeValueAsString(new Shipment(id, "P-100", "DELIVERED"));
        queue.sendMessage(r -> r.queueUrl(url).messageBody(body));
    }

    private List<String> receiptEventIds() {
        return objects.listObjectsV2Paginator(r -> r.bucket(Worker.BUCKET)).contents().stream()
                .map(obj -> objects.getObjectAsBytes(r -> r.bucket(Worker.BUCKET).key(obj.key())).asUtf8String())
                .map(body -> Worker.JSON.readValue(body, Shipment.class).eventId()).toList();
    }

    private void assertQueueDrained() {
        var counts = queue.getQueueAttributes(r -> r.queueUrl(url).attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
        assertThat(counts.values()).as("Completed deliveries must be acknowledged").containsOnly("0");
    }

    @Test void concurrentDuplicatesProduceOneReceipt() throws Exception {
        send("dup");
        send("dup");
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> consume = () -> { Worker.consume(queue, objects, false); return null; };
            for (var result : pool.invokeAll(List.of(consume, consume))) result.get();
        }
        assertThat(receiptEventIds()).as("Two workers handling the same business event").containsExactly("dup");
        assertQueueDrained();
    }

    @Test void differentEventsOnTheSameParcelProduceTwoReceipts() throws Exception {
        send("event-a");
        send("event-b");
        Worker.consume(queue, objects, false);
        Worker.consume(queue, objects, false);
        assertThat(receiptEventIds()).containsExactlyInAnyOrder("event-a", "event-b");
        assertQueueDrained();
    }

    @Test void crashAfterPersistenceCanBeRetriedWithoutAnotherReceipt() throws Exception {
        send("replay");
        assertThatThrownBy(() -> Worker.consume(queue, objects, true)).isInstanceOf(IllegalStateException.class);
        assertThat(receiptEventIds()).containsExactly("replay");
        Thread.sleep(5500); // Worker's SQS visibility window is five seconds.
        Worker.consume(queue, objects, false);
        assertThat(receiptEventIds()).as("A replay must preserve the first business result").containsExactly("replay");
        assertQueueDrained();
    }

    @Test void aFailedWriteLeavesTheEventAvailableForRecovery() throws Exception {
        objects.deleteBucket(r -> r.bucket(Worker.BUCKET));
        send("retry-write");
        try {
            Worker.consume(queue, objects, false);
        } catch (S3Exception expected) {
            // A worker may report a failed write directly or log it before returning.
        }
        objects.createBucket(r -> r.bucket(Worker.BUCKET));
        Thread.sleep(5500);
        Worker.consume(queue, objects, false);
        assertThat(receiptEventIds()).as("A failed persistence attempt must not acknowledge the event")
                .containsExactly("retry-write");
        assertQueueDrained();
    }
}
