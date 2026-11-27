package study.locker.reservation.integration;

import static io.grpc.Status.Code.FAILED_PRECONDITION;
import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.reservation.integration.GrpcFailureAssert.assertThatGrpcFailure;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
        assertThatGrpcFailure(() -> client(customer).collect(expired))
                .hasCode(FAILED_PRECONDITION)
                .hasDescriptionContaining("expired");
        client(customer).reissue(cmd(id));
        String fresh = lastCode();
        assertThatGrpcFailure(() -> client(customer).collect(cmd(id).toBuilder().setCode(old).build()))
                .hasCode(FAILED_PRECONDITION)
                .hasDescriptionContaining("Invalid pickup code");
        var collect = cmd(id).toBuilder().setCode(fresh).build();
        assertThat(client(customer).collect(collect).getState()).isEqualTo("COLLECTED");
        assertThat(client(customer).collect(collect).getState()).isEqualTo("COLLECTED");
        assertThatGrpcFailure(() -> client(customer).collect(cmd(id).toBuilder().setCode(fresh).build()))
                .hasCode(FAILED_PRECONDITION);
        assertThat(reserve().getState()).isEqualTo("RESERVED");
    }

    @Test
    void repeatedBadRequestCountsOnceAndConfiguredAttemptsLockTheCode() {
        String id = reserve().getId(), code = deposit(id);
        Command bad = cmd(id).toBuilder().setCode("wrong").build();
        for (int i = 0; i < 6; i++) {
            assertThatGrpcFailure(() -> client(customer).collect(bad))
                    .hasCode(FAILED_PRECONDITION)
                    .hasDescriptionContaining("Invalid pickup code");
        }
        assertThat(db.sql("select attempts from booking").query(Integer.class).single()).isOne();
        for (int i = 1; i < 3; i++) {
            assertThatGrpcFailure(() -> client(customer).collect(cmd(id)))
                    .hasCode(FAILED_PRECONDITION)
                    .hasDescriptionContaining("Invalid pickup code");
        }
        assertThatGrpcFailure(() -> client(customer).collect(cmd(id).toBuilder().setCode(code).build()))
                .hasCode(FAILED_PRECONDITION)
                .hasDescriptionContaining("locked");
    }

    @Test
    void canReplayAndCollectAPreJpaReservation() {
        new ResourceDatabasePopulator(new ClassPathResource("legacy-reservation.sql")).execute(dbSource);
        String id = "20260000-0000-4000-8000-000000000002";
        var replay = client(customer).reserve(
                cmd("CENTRAL-S1").toBuilder().setRequestId("20260000-0000-4000-8000-000000000001").build());
        assertThat(replay.getState()).isEqualTo("RESERVED");
        assertThat(replay.getId()).isEqualTo(id);
        assertThat(db.sql("select pickup_max_attempts from booking").query(Integer.class).single()).isEqualTo(5);
        for (int i = 0; i < 4; i++) {
            assertThatGrpcFailure(() -> client(customer).collect(cmd(id)))
                    .hasCode(FAILED_PRECONDITION)
                    .hasDescriptionContaining("Invalid pickup code");
        }
        ReservationTestBeans.now.set(ReservationTestBeans.now.get().plusSeconds(899));
        assertThat(client(customer).collect(cmd(id).toBuilder().setCode("01234567").build()).getState()).isEqualTo(
                "COLLECTED");
    }
}
