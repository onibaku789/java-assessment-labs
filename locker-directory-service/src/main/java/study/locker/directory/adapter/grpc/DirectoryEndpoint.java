package study.locker.directory.adapter.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import study.locker.common.Rpc;
import study.locker.directory.application.LockerDirectory;
import study.locker.wire.*;

public final class DirectoryEndpoint extends DirectoryGrpc.DirectoryImplBase {
    private final LockerDirectory directory;

    public DirectoryEndpoint(LockerDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void searchCompartments(Search q, StreamObserver<Compartments> out) {
        Rpc.reply(
                out,
                () ->
                        Compartments.newBuilder()
                                .addAllItems(
                                        directory.compartmentsAt(q.getSite(), q.getSize()).stream()
                                                .map(DirectoryEndpoint::wire)
                                                .toList())
                                .build());
    }

    @Override
    public void getCompartment(Key q, StreamObserver<Compartment> out) {
        Rpc.reply(
                out,
                () ->
                        directory
                                .find(q.getId())
                                .map(DirectoryEndpoint::wire)
                                .orElseThrow(
                                        () ->
                                                Status.NOT_FOUND
                                                        .withDescription("Unknown compartment")
                                                        .asRuntimeException()));
    }

    private static Compartment wire(study.locker.directory.domain.model.Compartment slot) {
        return Compartment.newBuilder()
                .setId(slot.id())
                .setSite(slot.site())
                .setSize(slot.size().value())
                .build();
    }
}
