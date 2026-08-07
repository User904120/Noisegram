package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoRuntimeStatus;

/** Internal bridge runtime state read by NoiseCore. */
public final class DuplicateVideoManagerState {

    private final DuplicateVideoRuntimeStatus status;
    private final DuplicateVideoMatchMode matchMode;

    DuplicateVideoManagerState(
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
