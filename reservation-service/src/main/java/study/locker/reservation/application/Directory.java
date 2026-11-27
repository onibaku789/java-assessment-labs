package study.locker.reservation.application;

import java.util.List;

public interface Directory {
    record Slot(String id, String site, String size) {}

    List<Slot> search(String site, String size);

    void requireCompartment(String id);
}
