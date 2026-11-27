package study.locker.common;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.function.Supplier;

public final class Rpc {
    private Rpc() {}

    public static Server serve(int port, BindableService service, ServerInterceptor... interceptors)
            throws IOException {
        return ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(service, interceptors))
                .build()
                .start();
    }

    public static ManagedChannel channel(String address) {
        return ManagedChannelBuilder.forTarget(address).usePlaintext().disableRetry().build();
    }

    public static <T> void reply(StreamObserver<T> observer, Supplier<T> call) {
        T result;
        try {
            result = call.get();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
            return;
        } catch (RuntimeException failure) {
            System.getLogger(Rpc.class.getName())
                    .log(
                            System.Logger.Level.ERROR,
                            "RPC failed with " + failure.getClass().getName());
            observer.onError(
                    Status.INTERNAL.withDescription("Service failure").asRuntimeException());
            return;
        }
        observer.onNext(result);
        observer.onCompleted();
    }
}
