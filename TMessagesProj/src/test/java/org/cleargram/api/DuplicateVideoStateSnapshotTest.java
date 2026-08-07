package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class DuplicateVideoStateSnapshotTest {

    @Test
    public void acceptsEveryApprovedStatusAndModeCombination() {
        assertSnapshot(DuplicateVideoRuntimeStatus.DISABLED, DuplicateVideoMatchMode.VIDEO);
        assertSnapshot(DuplicateVideoRuntimeStatus.DISABLED, DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        assertSnapshot(DuplicateVideoRuntimeStatus.READY, DuplicateVideoMatchMode.VIDEO);
        assertSnapshot(DuplicateVideoRuntimeStatus.READY, DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        assertSnapshot(DuplicateVideoRuntimeStatus.FAILED, DuplicateVideoMatchMode.VIDEO);
        assertSnapshot(DuplicateVideoRuntimeStatus.FAILED, DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        DuplicateVideoStateSnapshot failedWithoutMode = new DuplicateVideoStateSnapshot(
                DuplicateVideoRuntimeStatus.FAILED, null);
        assertNull(failedWithoutMode.getMatchMode());
    }

    @Test
    public void rejectsNullStatusAndMissingRequiredMode() {
        expectNull(() -> new DuplicateVideoStateSnapshot(null, DuplicateVideoMatchMode.VIDEO));
        expectNull(() -> new DuplicateVideoStateSnapshot(DuplicateVideoRuntimeStatus.DISABLED, null));
        expectNull(() -> new DuplicateVideoStateSnapshot(DuplicateVideoRuntimeStatus.READY, null));
    }

    private static void assertSnapshot(
            DuplicateVideoRuntimeStatus status,
            DuplicateVideoMatchMode mode
    ) {
        DuplicateVideoStateSnapshot snapshot = new DuplicateVideoStateSnapshot(status, mode);
        assertEquals(status, snapshot.getStatus());
        assertEquals(mode, snapshot.getMatchMode());
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); } catch (NullPointerException expected) { }
    }
}
