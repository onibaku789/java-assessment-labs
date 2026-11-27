package study.locker.receipt.application;

import study.locker.receipt.domain.model.Receipt;

public record StoreLifecycleReceipt(Archive archive) {
    public interface Archive {
        boolean createIfAbsent(Receipt receipt);

        Receipt existing(Receipt receipt);
    }

    public enum Outcome {
        STORED,
        REPLAYED
    }

    public Outcome store(Receipt receipt) {
        if (archive.createIfAbsent(receipt)) return Outcome.STORED;
        if (!receipt.equals(archive.existing(receipt)))
            throw new IllegalStateException("Conflicting event ID");
        return Outcome.REPLAYED;
    }
}
