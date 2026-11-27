package study.locker.reservation.adapter.persistence;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface BookingRows extends JpaRepository<BookingRow, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookingRow> findForUpdateById(UUID id);

    @Query(
            """
            select b.compartment from BookingRow b
            where b.state in ('RESERVED', 'DEPOSITED') and b.compartment in :candidates
            """)
    Set<String> findOccupiedCompartments(List<String> candidates);

    @Modifying
    @NativeQuery(
            """
            insert into booking (id, compartment, owner_sub, email, state)
            values (:id, :compartment, :owner, :email, 'RESERVED')
            on conflict do nothing
            """)
    int insertIfAvailable(UUID id, String compartment, String owner, String email);
}
