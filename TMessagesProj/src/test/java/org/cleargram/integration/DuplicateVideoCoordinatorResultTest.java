package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class DuplicateVideoCoordinatorResultTest {

    @Test
    public void preservesSnapshotOrderAndPublishesImmutablePresentationIds() {
        List<Long> itemIds = new ArrayList<>(Arrays.asList(8L, 2L, 5L));
        List<Long> hiddenIds = new ArrayList<>(Collections.singletonList(2L));

        DuplicateVideoCoordinatorResult result = new DuplicateVideoCoordinatorResult(
                single(11L), 4L, itemIds, hiddenIds, false);
        itemIds.clear();
        hiddenIds.clear();

        assertEquals(4L, result.getGeneration());
        assertEquals(Arrays.asList(8L, 2L, 5L), result.getPresentationItemIds());
        assertEquals(Collections.singletonList(2L), result.getHiddenPresentationItemIds());
        try {
            result.getPresentationItemIds().add(9L);
            fail("Expected immutable presentation IDs");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void rejectsUnknownOrDuplicatePresentationIds() {
        expectIllegal(() -> new DuplicateVideoCoordinatorResult(
                single(1L), 1L, Arrays.asList(1L, 1L), Collections.<Long>emptyList(), false));
        expectIllegal(() -> new DuplicateVideoCoordinatorResult(
                single(1L), 1L, Collections.singletonList(1L), Collections.singletonList(2L), false));
        expectIllegal(() -> new DuplicateVideoCoordinatorResult(
                single(1L), 1L, Collections.singletonList(1L), Arrays.asList(1L, 1L), false));
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static LogicalPresentationId single(long messageId) {
        return LogicalPresentationId.singleMessage(0, 1L, 0L, messageId);
    }
}
