package study.locker.directory.adapter.persistence;

import jakarta.persistence.*;

import study.locker.directory.domain.model.Compartment;

@Entity
@Table(name = "compartment")
class CompartmentRow {
    protected CompartmentRow() {}

    @Id String id;
    String site;
    String size;

    Compartment toDomain() {
        return new Compartment(id, site, new Compartment.Size(size));
    }
}
