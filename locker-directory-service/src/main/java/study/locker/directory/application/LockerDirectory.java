package study.locker.directory.application;

import study.locker.directory.domain.model.Compartment;

import java.util.List;
import java.util.Optional;

public record LockerDirectory(Catalog catalog) {
    public interface Catalog {
        List<Compartment> search(String site, String size);

        Optional<Compartment> find(String id);
    }

    public List<Compartment> compartmentsAt(String site, String size) {
        return catalog.search(site, size);
    }

    public Optional<Compartment> find(String id) {
        return catalog.find(id);
    }
}
