package study.locker.reservation.integration;

import static io.grpc.Status.Code.INTERNAL;
import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.reservation.integration.GrpcFailureAssert.assertThatGrpcFailure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

class ReservationCommitIT extends ReservationFixture {

    @Test
    void commitFailureRollsBackJournalBookingAndOutboxBeforeSuccessIsLogged() {
        var request = cmd("CENTRAL-S1");
        Logger logger = (Logger) LoggerFactory.getLogger("locker.events");
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            // The random request ID differs from the booking ID. PostgreSQL checks this FK at
            // commit.
            db.sql("alter table command_result add constraint test_commit_failure foreign" +
                    " key (request_id) references booking(id) deferrable initially" + " deferred").update();
            try {
                assertThatGrpcFailure(() -> client(customer).reserve(request)).hasCode(INTERNAL);
                assertRowCounts(0, 0, 0);
                assertThat(capture.list).filteredOn(e -> e.getMessage().equals("reservation_command")).isEmpty();
            } finally {
                db.sql("alter table command_result drop constraint test_commit_failure").update();
            }
            assertThat(client(customer).reserve(request).getState()).isEqualTo("RESERVED");
            assertRowCounts(1, 1, 1);
            assertThat(capture.list).filteredOn(e -> e.getMessage().equals("reservation_command")).singleElement()
                    .satisfies(event -> assertThat(event.getKeyValuePairs()).anyMatch(
                            pair -> pair.key.equals("outcome") && pair.value.equals("committed")));
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
