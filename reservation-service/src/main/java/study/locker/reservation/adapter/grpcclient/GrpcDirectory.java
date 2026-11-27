package study.locker.reservation.adapter.grpcclient;

import study.locker.reservation.application.Directory;
import study.locker.wire.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public record GrpcDirectory(DirectoryGrpc.DirectoryBlockingStub stub, Duration deadline)
        implements Directory {
    public GrpcDirectory {
        if (!deadline.isPositive())
            throw new IllegalArgumentException("Directory deadline must be positive");
    }

    public List<Slot> search(String site, String size) {
        return stub
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .searchCompartments(Search.newBuilder().setSite(site).setSize(size).build())
                .getItemsList()
                .stream()
                .map(c -> new Slot(c.getId(), c.getSite(), c.getSize()))
                .toList();
    }

    public void requireCompartment(String id) {
        stub.withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .getCompartment(Key.newBuilder().setId(id).build());
    }
}
