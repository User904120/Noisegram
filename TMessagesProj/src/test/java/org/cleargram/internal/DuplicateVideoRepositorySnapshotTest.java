package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoMatchMode;

public final class DuplicateVideoRepositorySnapshotTest {

    @Test
    public void acceptsApprovedReadinessStates() {
        DuplicateVideoRepositorySnapshot loading = new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.LOADING, false, null);
        assertEquals(DuplicateVideoRepositoryReadiness.LOADING, loading.getReadiness());
        assertFalse(loading.isEnabled());
        assertNull(loading.getMatchMode());

        DuplicateVideoRepositorySnapshot ready = new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.READY, true, DuplicateVideoMatchMode.VIDEO);
        assertTrue(ready.isEnabled());

        DuplicateVideoRepositorySnapshot failed = new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.FAILED, true, null);
        assertNull(failed.getMatchMode());
    }

    @Test
    public void rejectsInvalidReadinessStateCombinations() {
        expectNull(() -> new DuplicateVideoRepositorySnapshot(null, false, null));
        expectNull(() -> new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.READY, false, null));
        expectIllegal(() -> new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.LOADING, true, null));
        expectIllegal(() -> new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.LOADING, false, DuplicateVideoMatchMode.VIDEO));
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
    }

    private static void expectIllegal(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
