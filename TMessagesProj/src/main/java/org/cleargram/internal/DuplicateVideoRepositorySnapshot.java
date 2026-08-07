package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.api.DuplicateVideoMatchMode;

/** Immutable internal Duplicate Video durable/readiness state. */
final class DuplicateVideoRepositorySnapshot {

    private final DuplicateVideoRepositoryReadiness readiness;
    private final boolean enabled;
    private final DuplicateVideoMatchMode matchMode;

    DuplicateVideoRepositorySnapshot(
            DuplicateVideoRepositoryReadiness readiness,
            boolean enabled,
            DuplicateVideoMatchMode matchMode
    ) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        if (readiness == DuplicateVideoRepositoryReadiness.LOADING) {
            if (enabled || matchMode != null) {
                throw new IllegalArgumentException("LOADING state must be disabled without a mode");
            }
        } else if (readiness == DuplicateVideoRepositoryReadiness.READY && matchMode == null) {
            throw new NullPointerException("matchMode");
        }
        this.enabled = enabled;
        this.matchMode = matchMode;
    }

    DuplicateVideoRepositoryReadiness getReadiness() {
        return readiness;
    }

    boolean isEnabled() {
        return enabled;
    }

    DuplicateVideoMatchMode getMatchMode() {
        return matchMode;
    }
}
