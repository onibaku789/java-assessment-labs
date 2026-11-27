package study.locker.reservation.adapter.persistence;

import jakarta.persistence.*;

import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "command_result")
class CommandRow implements Persistable<CommandRow.Key> {
    @Embeddable
    record Key(String actor, @Column(name = "request_id") UUID requestId) implements Serializable {
    }

    @EmbeddedId
    private Key key;

    String fingerprint;

    @Column(columnDefinition = "bytea")
    byte[] result;

    String error;

    @Transient
    private boolean newEntry = true;

    protected CommandRow() {
    }

    CommandRow(Key key, String fingerprint, byte[] result, String error) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.result = result;
        this.error = error;
    }

    @Override
    public Key getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return newEntry;
    }

    @PostPersist
    @PostLoad
    private void markPersisted() {
        newEntry = false;
    }
}
