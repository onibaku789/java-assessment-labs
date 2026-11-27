package study.locker.reservation.adapter.grpcclient;

import study.locker.common.RpcCalls;
import study.locker.reservation.application.Directory;
import study.locker.wire.*;

import java.util.List;

public record GrpcDirectory(DirectoryGrpc.DirectoryBlockingStub stub, RpcCalls calls)
        implements Directory {
    public List<Slot> search(String site, String size) {
        var query = Search.newBuilder().setSite(site).setSize(size).build();
        return calls
                .once(deadline -> stub.withDeadline(deadline).searchCompartments(query))
                .getItemsList()
                .stream()
                .map(c -> new Slot(c.getId(), c.getSite(), c.getSize()))
                .toList();
    }

    public void requireCompartment(String id) {
        var key = Key.newBuilder().setId(id).build();
        calls.once(deadline -> stub.withDeadline(deadline).getCompartment(key));
    }
}
