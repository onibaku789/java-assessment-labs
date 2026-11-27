package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.application.*;
import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.wire.*;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

class ReservationBookIT extends ReservationFixture {
    @Autowired
    ReservationStore store;
    @Autowired
    TransactionTemplate tx;


    @Test
    void successfulReplayReturnsOriginalResultAndDifferentInputConflicts() {
        Command command = cmd("CENTRAL-S1");
        Booking before = execute(customer, Action.RESERVE, command);
        execute(customer, Action.CANCEL, cmd(before.getId()));
        assertThat(execute(customer, Action.RESERVE, command)).isEqualTo(before);
        assertThatThrownBy(
                () -> execute(customer, Action.RESERVE, command.toBuilder().setId("CENTRAL-L1").build())).isInstanceOf(
                StatusRuntimeException.class).hasMessageContaining("ALREADY_EXISTS");
        assertThat(reserve().getId()).isNotEqualTo(before.getId());
    }

    @Test
    void concurrentReservationsHaveExactlyOneWinner() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Status.Code>>();
            for (int i = 0; i < 8; i++)
                futures.add(threads.submit(() -> {
                    start.await();
                    try {
                        reserve();
                        return Status.Code.OK;
                    } catch (StatusRuntimeException ex) {
                        return ex.getStatus().getCode();
                    }
                }));
            start.countDown();
            var results = new ArrayList<Status.Code>();
            for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            assertThat(results).containsOnly(Status.Code.OK, Status.Code.ALREADY_EXISTS);
            assertThat(Collections.frequency(results, Status.Code.OK)).isOne();
            assertThat(db.sql("select count(*) from outbox").query(Integer.class).single()).isOne();
        }
    }

    @Test
    void concurrentRetriesCommitOnlyOneBookingAndOneOutboxEvent() throws Exception {
        Command request = cmd("CENTRAL-S1");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CyclicBarrier(4);
            List<Future<Booking>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                results.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return client(customer).reserve(request);
                }));
            Booking original = results.getFirst().get(10, TimeUnit.SECONDS);
            for (var result : results)
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(original);
        }
        assertRowCounts(1, 1, 1);
    }

    @Test
    void lockingAReservationAllowsReadsButSerializesUpdates() {
        var id = UUID.fromString(reserve().getId());
        var otherTransaction = ReservationTestDatabase.independentTransaction(tx);

        tx.executeWithoutResult(holder -> {
            assertThat(store.find(id, true)).isPresent();
            otherTransaction.executeWithoutResult(reader -> {
                db.sql("set local lock_timeout = '500ms'").update();
                assertThat(store.find(id, false)).isPresent();
            });
            assertThatThrownBy(() -> otherTransaction.executeWithoutResult(writer -> {
                db.sql("set local lock_timeout = '500ms'").update();
                store.find(id, true);
            })).rootCause().isInstanceOfSatisfying(SQLException.class,
                    failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
        });

        tx.executeWithoutResult(nextWriter -> assertThat(store.find(id, true)).isPresent());
    }
}
