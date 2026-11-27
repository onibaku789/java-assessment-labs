package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.wire.*;

import java.time.*;

class ReservationPickupIT extends ReservationFixture {
    @Autowired
    javax.sql.DataSource dbSource;

    @Test
    void expiredCodeCanBeReissuedAndCollectedExactlyOnce() {
        String id = reserve().getId(), old = deposit(id);
        assertThat(db.sql("select pickup_max_attempts from booking").query(Integer.class).single()).isEqualTo(3);
        assertThat(db.sql("select expires_at from booking").query(OffsetDateTime.class).single().toInstant()).isEqualTo(
                ReservationTestBeans.now.get().plusSeconds(45));
        ReservationTestBeans.now.set(ReservationTestBeans.now.get().plusSeconds(45));
        Command expired = cmd(id).toBuilder().setCode(old).build();
        assertThatThrownBy(() -> execute(customer, Action.COLLECT, expired)).hasMessageContaining("expired");
        execute(customer, Action.REISSUE, cmd(id));
        String fresh = lastCode();
        assertThatThrownBy(
                () -> execute(customer, Action.COLLECT, cmd(id).toBuilder().setCode(old).build())).hasMessageContaining(
                "Invalid pickup code");
        var collect = cmd(id).toBuilder().setCode(fresh).build();
        assertThat(execute(customer, Action.COLLECT, collect).getState()).isEqualTo("COLLECTED");
        assertThat(execute(customer, Action.COLLECT, collect).getState()).isEqualTo("COLLECTED");
        assertThatThrownBy(() -> execute(customer, Action.COLLECT,
                cmd(id).toBuilder().setCode(fresh).build())).hasMessageContaining("FAILED_PRECONDITION");
        assertThat(reserve().getState()).isEqualTo("RESERVED");
    }

    @Test
    void repeatedBadRequestCountsOnceAndConfiguredAttemptsLockTheCode() {
        String id = reserve().getId(), code = deposit(id);
        Command bad = cmd(id).toBuilder().setCode("wrong").build();
        for (int i = 0; i < 6; i++)
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, bad)).hasMessageContaining(
                    "Invalid pickup code");
        assertThat(db.sql("select attempts from booking").query(Integer.class).single()).isOne();
        for (int i = 1; i < 3; i++)
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, cmd(id))).hasMessageContaining(
                    "Invalid pickup code");
        assertThatThrownBy(() -> execute(customer, Action.COLLECT,
                cmd(id).toBuilder().setCode(code).build())).hasMessageContaining("locked");
    }

    @Test
    void canReplayAndCollectAPreJpaReservation() {
        new ResourceDatabasePopulator(new ClassPathResource("legacy-reservation.sql")).execute(dbSource);
        String id = "20260000-0000-4000-8000-000000000002";
        var replay = execute(customer, Action.RESERVE,
                cmd("CENTRAL-S1").toBuilder().setRequestId("20260000-0000-4000-8000-000000000001").build());
        assertThat(replay.getState()).isEqualTo("RESERVED");
        assertThat(replay.getId()).isEqualTo(id);
        assertThat(db.sql("select pickup_max_attempts from booking").query(Integer.class).single()).isEqualTo(5);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, cmd(id))).hasMessageContaining(
                    "Invalid pickup code");
        }
        ReservationTestBeans.now.set(ReservationTestBeans.now.get().plusSeconds(899));
        assertThat(execute(customer, Action.COLLECT,
                cmd(id).toBuilder().setCode("01234567").build()).getState()).isEqualTo("COLLECTED");
    }
}
