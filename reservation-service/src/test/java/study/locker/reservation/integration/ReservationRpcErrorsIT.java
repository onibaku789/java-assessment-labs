package study.locker.reservation.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRpcErrorsIT extends ReservationTestBeans {
    @Autowired
    @Qualifier("reservationRpcErrors")
    GrpcExceptionHandler errors;

    @Test
    void unexpectedFailureExposesOnlyTheGenericStatusAndLogsItsTypeOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger("study.locker.reservation.adapter.grpcserver.ReservationRpcErrors");
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            var result = errors.handleException(new IllegalStateException("private diagnostic detail"));
            assertThat(result.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
            assertThat(result.getStatus().getDescription()).isEqualTo("Service failure");
            assertThat(capture.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("Reservation RPC failed with java.lang.IllegalStateException");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void forwardsGrpcStatusAndTrailers() {
        var key = Metadata.Key.of("test-detail", Metadata.ASCII_STRING_MARSHALLER);
        var trailers = new Metadata();
        trailers.put(key, "upstream");
        var status = Status.UNAVAILABLE.withDescription("dependency unavailable");
        var checked = status.asException(trailers);
        assertThat(errors.handleException(checked)).isSameAs(checked);
        var converted = errors.handleException(status.asRuntimeException(trailers));
        assertThat(converted.getStatus()).isEqualTo(status);
        assertThat(converted.getTrailers().get(key)).isEqualTo("upstream");
    }
}
