package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.DuplicateVideoBatch;

/** Immutable outcome of one hashing pass. */
public final class DuplicateVideoHashingResult {

    private final DuplicateVideoBatch batchOrNull;
    private final List<Long> failedPresentationItemIds;

    DuplicateVideoHashingResult(DuplicateVideoBatch batchOrNull, List<Long> failedPresentationItemIds) {
        Objects.requireNonNull(failedPresentationItemIds, "failedPresentationItemIds");
        this.batchOrNull = batchOrNull;
        this.failedPresentationItemIds = Collections.unmodifiableList(
                new ArrayList<>(failedPresentationItemIds));
    }

    public boolean hasBatch() {
        return batchOrNull != null;
    }

    public DuplicateVideoBatch getBatchOrNull() {
        return batchOrNull;
    }

    public List<Long> getFailedPresentationItemIds() {
        return failedPresentationItemIds;
    }
}
