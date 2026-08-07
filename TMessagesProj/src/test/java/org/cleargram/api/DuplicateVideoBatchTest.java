package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class DuplicateVideoBatchTest {

    @Test
    public void acceptsBothModesAndPreservesOrderAndVisibleMediaFlag() {
        for (DuplicateVideoMatchMode mode : DuplicateVideoMatchMode.values()) {
            DuplicateVideoItem first = item(4L, (byte) 1);
            DuplicateVideoItem second = item(-2L, (byte) 1);
            DuplicateVideoBatch batch = new DuplicateVideoBatch(mode, 1,
                    Arrays.asList(first, second), true);
            assertEquals(mode, batch.getMatchMode());
            assertEquals(1, batch.getKeyVersion());
            assertEquals(Arrays.asList(first, second), batch.getItems());
            assertTrue(batch.hasOtherVisibleMedia());
        }
    }

    @Test
    public void rejectsInvalidArguments() {
        expectNull(() -> new DuplicateVideoBatch(null, 1, Collections.singletonList(item(1L, (byte) 1)), false));
        expectIllegal(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 0, Collections.singletonList(item(1L, (byte) 1)), false));
        expectIllegal(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, -1, Collections.singletonList(item(1L, (byte) 1)), false));
        expectIllegal(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 2, Collections.singletonList(item(1L, (byte) 1)), false));
        expectNull(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1, null, false));
        expectIllegal(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1, Collections.<DuplicateVideoItem>emptyList(), false));
        expectNull(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1, Arrays.asList(item(1L, (byte) 1), null), false));
        expectIllegal(() -> new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(item(1L, (byte) 1), item(1L, (byte) 2)), false));
    }

    @Test
    public void allowsSameMatchKeyForDifferentPresentationIds() {
        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(item(1L, (byte) 7), item(2L, (byte) 7)), false);
        assertFalse(batch.hasOtherVisibleMedia());
        assertEquals(2, batch.getItems().size());
    }

    @Test
    public void defensivelyCopiesAndPublishesImmutableItemList() {
        List<DuplicateVideoItem> source = new ArrayList<>();
        DuplicateVideoItem first = item(1L, (byte) 1);
        source.add(first);
        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1, source, false);
        source.clear();
        assertEquals(Collections.singletonList(first), batch.getItems());
        try {
            batch.getItems().add(item(2L, (byte) 2));
            fail("Expected immutable items");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static DuplicateVideoItem item(long id, byte value) {
        return new DuplicateVideoItem(id, DuplicateVideoItemTest.key(value));
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); } catch (NullPointerException expected) { }
    }

    private static void expectIllegal(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); } catch (IllegalArgumentException expected) { }
    }
}
