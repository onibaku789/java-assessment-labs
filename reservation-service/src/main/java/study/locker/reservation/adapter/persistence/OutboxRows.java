package study.locker.reservation.adapter.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

import study.locker.reservation.application.PublishOutbox;

import java.util.List;

interface OutboxRows extends JpaRepository<OutboxRow, Long>, PublishOutbox.Queue {
    // All relay instances share this lock until the publishing transaction ends.
    @Override
    @NativeQuery("select pg_try_advisory_xact_lock(817493)")
    boolean claimBatch();

    List<OutboxRow> findByOrderBySeqAsc(Limit limit);

    @Override
    default List<PublishOutbox.Message> pending(int batchSize) {
        return findByOrderBySeqAsc(Limit.of(batchSize)).stream().map(OutboxRow::message).toList();
    }

    @Override
    default void acknowledge(long sequence) {
        deleteById(sequence);
    }
}
