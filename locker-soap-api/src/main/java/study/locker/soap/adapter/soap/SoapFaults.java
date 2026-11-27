package study.locker.soap.adapter.soap;

import study.locker.edge.application.*;
import study.locker.soap.wire.*;

import java.util.Objects;
import java.util.function.Supplier;

final class SoapFaults {
    private SoapFaults() {
    }

    static <T> T call(Supplier<T> op) throws ProblemMessage {
        try {
            return op.get();
        } catch (GatewayFailure ex) {
            var fault = new Problem();
            fault.setCode(ex.code());
            fault.setMessage(Objects.toString(ex.getMessage(), fault.getCode()));
            throw new ProblemMessage(fault.getMessage(), fault);
        }
    }
}
