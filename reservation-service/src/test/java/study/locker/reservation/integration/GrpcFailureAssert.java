package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

final class GrpcFailureAssert extends AbstractAssert<GrpcFailureAssert, StatusRuntimeException> {
    private static final InstanceOfAssertFactory<StatusRuntimeException, GrpcFailureAssert> GRPC_FAILURE =
            new InstanceOfAssertFactory<>(StatusRuntimeException.class, GrpcFailureAssert::new);

    private GrpcFailureAssert(StatusRuntimeException failure) {
        super(failure, GrpcFailureAssert.class);
    }

    static GrpcFailureAssert assertThatGrpcFailure(ThrowingCallable call) {
        return assertThatThrownBy(call).asInstanceOf(GRPC_FAILURE);
    }

    GrpcFailureAssert hasCode(Status.Code expected) {
        assertThat(actual.getStatus().getCode()).as("gRPC status code").isEqualTo(expected);
        return this;
    }

    GrpcFailureAssert hasDescriptionContaining(String expected) {
        assertThat(actual.getStatus().getDescription()).as("gRPC status description").contains(expected);
        return this;
    }
}
