package org.cleargram.api;

import java.util.Objects;

/** Immutable Core-owned Duplicate Video runtime state. */
public final class DuplicateVideoStateSnapshot {

    private final DuplicateVideoRuntimeStatus status;
    private final DuplicateVideoMatchMode matchMode;

    DuplicateVideoStateSnapshot(
            DuplicateVideoRuntimeStatus status,
            DuplicateVideoMatchMode matchMode
    ) {
        this.status = Objects.requireNonNull(status, "status");
        if (status != DuplicateVideoRuntimeStatus.FAILED) {
            this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
        } else {
            this.matchMode = matchMode;
        }
    }

    public DuplicateVideoRuntimeStatus getStatus() {
        return status;
    }

    public DuplicateVideoMatchMode getMatchMode() {
        return matchMode;
    }
}
