package study.locker.reservation.adapter.grpcclient;

import study.locker.common.RpcGuard;
import study.locker.reservation.application.Directory;
import study.locker.wire.*;

import java.util.List;

public record GrpcDirectory(DirectoryGrpc.DirectoryBlockingStub stub, RpcGuard guard)
        implements Directory {
    public List<Slot> search(String site, String size) {
        var query = Search.newBuilder().setSite(site).setSize(size).build();
        return guard
                .call(false, deadline -> stub.withDeadline(deadline).searchCompartments(query))
                .getItemsList()
                .stream()
                .map(c -> new Slot(c.getId(), c.getSite(), c.getSize()))
                .toList();
    }

    public void requireCompartment(String id) {
        var key = Key.newBuilder().setId(id).build();
        guard.call(false, deadline -> stub.withDeadline(deadline).getCompartment(key));
    }
}
