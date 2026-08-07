package org.cleargram.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.NoiseAction;

/** Immutable initial-load state exchanged through the Black List storage SPI. */
public final class BlackListStorageState {

    private final NoiseAction action;
    private final List<BlackListStorageRecord> records;

    public BlackListStorageState(NoiseAction action, List<BlackListStorageRecord> records) {
        this.action = Objects.requireNonNull(action, "action");
        Objects.requireNonNull(records, "records");
        List<BlackListStorageRecord> copy = new ArrayList<>(records.size());
        for (BlackListStorageRecord record : records) {
            copy.add(Objects.requireNonNull(record, "records must not contain null"));
        }
        this.records = Collections.unmodifiableList(copy);
    }

    public NoiseAction getAction() {
        return action;
    }

    public List<BlackListStorageRecord> getRecords() {
        return records;
    }
}
