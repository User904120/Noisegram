package org.cleargram.api;

/** Test-only creator for the package-private Core-owned snapshot constructor. */
public final class DuplicateVideoStateSnapshotTestFactory {

    private DuplicateVideoStateSnapshotTestFactory() {
    }

    public static DuplicateVideoStateSnapshot create(
            DuplicateVideoRuntimeStatus status,
            DuplicateVideoMatchMode matchMode
    ) {
        return new DuplicateVideoStateSnapshot(status, matchMode);
    }
}
