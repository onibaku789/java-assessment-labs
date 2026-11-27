package study.locker.reservation.application;


import java.util.*;

public interface CommandJournal {

    record Entry(String fingerprint, CommandHandler.Result result) {}

    void lock(String subject, UUID requestId);

    Optional<Entry> find(String subject, UUID requestId);

    void save(String subject, UUID requestId, Entry entry);
}
