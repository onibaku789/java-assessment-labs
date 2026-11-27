package study.locker.edge.application;

import java.util.List;

public interface LockerOperations {
    enum Action {
        RESERVE,
        CANCEL,
        DEPOSIT,
        REISSUE,
        COLLECT
    }

    record Slot(String id, String site, String size) {
    }

    record Reservation(String id, String compartmentId, String state) {
    }

    List<Slot> available(String credential, String site, String size);

    Reservation get(String credential, String id);

    Reservation command(String credential, Action action, String requestId, String id, String code);
}
