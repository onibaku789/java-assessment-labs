package study.locker.directory.adapter.persistence;

import org.springframework.stereotype.Repository;

import study.locker.directory.application.LockerDirectory;
import study.locker.directory.domain.model.Compartment;

import java.util.*;

@Repository
class JpaCatalog implements LockerDirectory.Catalog {
    private final CompartmentRows rows;

    JpaCatalog(CompartmentRows rows) {
        this.rows = rows;
    }

    public List<Compartment> search(String site, String size) {
        return rows.search(site, size).stream().map(CompartmentRow::toDomain).toList();
    }

    public Optional<Compartment> find(String id) {
        return rows.findById(id).map(CompartmentRow::toDomain);
    }
}
