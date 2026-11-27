package study.locker.edge.integration;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import study.locker.common.Rpc;
import study.locker.wire.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

final class ReservationProbe extends ReservationsGrpc.ReservationsImplBase implements ServerInterceptor {
    private static final Context.Key<String> AUTHORIZATION = Context.key("test-authorization");
    final List<String> calls = new CopyOnWriteArrayList<>();
    volatile long remainingMillis;
    final CompletableFuture<Void> held = new CompletableFuture<>();
    final CompletableFuture<Void> release = new CompletableFuture<>();
    private Server server;
    private ManagedChannel channel;

    ManagedChannel start() throws Exception {
        server = Rpc.serve(0, this, this);
        channel = Rpc.channel("127.0.0.1:" + server.getPort());
        return channel;
    }

    void close() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        var context = Context.current().withValue(AUTHORIZATION, headers.get(Rpc.AUTHORIZATION));
        return Contexts.interceptCall(context, call, headers, next);
    }

    @Override
    public void get(Key request, StreamObserver<Booking> reply) {
        String id = request.getId();
        calls.add(id + " " + AUTHORIZATION.get());
        if (id.equals("offline") || (id.equals("retry") && calls.size() == 1)) {
            reply.onError(Status.UNAVAILABLE.asRuntimeException());
            return;
        }
        if (id.equals("stalled")) {
            remainingMillis = Context.current().getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
            return;
        }
        if (id.equals("held")) {
            held.complete(null);
            release.thenRun(() -> completeLookup(id, reply));
            return;
        }
        completeLookup(id, reply);
    }

    private static void completeLookup(String id, StreamObserver<Booking> reply) {
        reply.onNext(Booking.newBuilder().setId(id).build());
        reply.onCompleted();
    }

    @Override
    public void available(Search query, StreamObserver<Compartments> reply) {
        calls.add("available");
        reply.onNext(Compartments.getDefaultInstance());
        reply.onCompleted();
    }

    @Override
    public void deposit(Command command, StreamObserver<Booking> reply) {
        calls.add(command.getRequestId());
        reply.onError(Status.UNAVAILABLE.asRuntimeException());
    }
}
