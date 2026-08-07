package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoRuntimeStatus;

public final class DuplicateVideoManagerBridgeTest {

    @Test
    public void resultDefensivelyCopiesAndPublishesImmutableIds() {
        List<Long> ids = new ArrayList<>(Arrays.asList(Long.MIN_VALUE, 2L));
        DuplicateVideoManagerResult result = new DuplicateVideoManagerResult(ids, true);
        ids.clear();
        assertEquals(Arrays.asList(Long.MIN_VALUE, 2L), result.getHiddenItemIds());
        assertTrue(result.shouldHideWholeMessage());
        try {
            result.getHiddenItemIds().add(3L);
            fail("Expected immutable ids");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test
    public void resultRejectsNullAndDuplicateIds() {
        expectNull(() -> new DuplicateVideoManagerResult(null, false));
        expectNull(() -> new DuplicateVideoManagerResult(Arrays.asList(1L, null), false));
        expectIllegal(() -> new DuplicateVideoManagerResult(Arrays.asList(1L, 1L), false));
    }

    @Test
    public void stateAcceptsApprovedCombinations() {
        assertEquals(DuplicateVideoMatchMode.VIDEO, new DuplicateVideoManagerState(
                DuplicateVideoRuntimeStatus.DISABLED, DuplicateVideoMatchMode.VIDEO).getMatchMode());
        assertEquals(DuplicateVideoMatchMode.VIDEO_AND_TEXT, new DuplicateVideoManagerState(
                DuplicateVideoRuntimeStatus.READY, DuplicateVideoMatchMode.VIDEO_AND_TEXT).getMatchMode());
        assertNull(new DuplicateVideoManagerState(DuplicateVideoRuntimeStatus.FAILED, null).getMatchMode());
    }

    @Test
    public void stateRejectsMissingRequiredModeAndNullStatus() {
        expectNull(() -> new DuplicateVideoManagerState(null, DuplicateVideoMatchMode.VIDEO));
        expectNull(() -> new DuplicateVideoManagerState(DuplicateVideoRuntimeStatus.DISABLED, null));
        expectNull(() -> new DuplicateVideoManagerState(DuplicateVideoRuntimeStatus.READY, null));
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
