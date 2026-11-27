package study.locker.reservation.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

interface CommandRows extends JpaRepository<CommandRow, CommandRow.Key> {
    // Concurrent retries must serialize even before the first journal row exists.
    @NativeQuery("select true from pg_advisory_xact_lock(hashtextextended(:key, 0))")
    void lockRequest(String key);
}
