package study.locker.reservation.application;

import java.util.*;

public record PublishOutbox(Queue queue, int batchSize, Publisher publisher)
        implements OutboxRelay {
    public record Message(
            long sequence,
            String topic,
            UUID reservation,
            UUID eventId,
            String payload,
            String traceParent) {}

    public interface Queue {
        boolean claimBatch();

        List<Message> pending(int batchSize);

        void acknowledge(long sequence);

        long count();
    }

    public enum Publication {
        ACKNOWLEDGED,
        FAILED,
        INTERRUPTED
    }

    public interface Publisher {
        Publication publish(Message message);
    }

    @Override
    public void publish() {
        if (!queue.claimBatch()) return;
        for (var message : queue.pending(batchSize)) {
            if (publisher.publish(message) != Publication.ACKNOWLEDGED) break;
            queue.acknowledge(message.sequence());
        }
    }
}
