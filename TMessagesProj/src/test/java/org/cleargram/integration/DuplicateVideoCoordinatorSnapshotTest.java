package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class DuplicateVideoCoordinatorSnapshotTest {

    @Test
    public void preservesImmutableOrderedTelegramFreeSnapshot() {
        List<DuplicateVideoCoordinatorSnapshot.Item> source = new ArrayList<>(Arrays.asList(
                item(4L, 0), item(-2L, 1)));

        DuplicateVideoCoordinatorSnapshot snapshot = new DuplicateVideoCoordinatorSnapshot(
                source, null, true);
        source.clear();

        assertEquals("", snapshot.getLogicalText());
        assertTrue(snapshot.hasOtherVisibleMedia());
        assertEquals(2, snapshot.getItems().size());
        assertEquals(4L, snapshot.getItems().get(0).getPresentationItemId());
        assertEquals(0, snapshot.getItems().get(0).getOrderedPosition());
        try {
            snapshot.getItems().add(item(7L, 2));
            fail("Expected immutable item list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void rejectsMissingAndUnstableOrderedItems() {
        expectNull(() -> new DuplicateVideoCoordinatorSnapshot(null, "", false));
        expectIllegal(() -> new DuplicateVideoCoordinatorSnapshot(
                Collections.<DuplicateVideoCoordinatorSnapshot.Item>emptyList(), "", false));
        expectNull(() -> new DuplicateVideoCoordinatorSnapshot(
                Arrays.asList(item(1L, 0), null), "", false));
        expectIllegal(() -> new DuplicateVideoCoordinatorSnapshot(
                Arrays.asList(item(1L, 0), item(1L, 1)), "", false));
        expectIllegal(() -> new DuplicateVideoCoordinatorSnapshot(
                Collections.singletonList(item(1L, 1)), "", false));
    }

    @Test
    public void descriptorDefensivelyCopiesPathAndRejectsInvalidSize() {
        File original = new File("relative/video.mp4");
        TelegramDuplicateVideoMediaSourceDescriptor descriptor =
                new TelegramDuplicateVideoMediaSourceDescriptor(original, 5L);

        assertEquals(original.getPath(), descriptor.getFile().getPath());
        assertFalse(original == descriptor.getFile());
        expectNull(() -> new TelegramDuplicateVideoMediaSourceDescriptor(null, 1L));
        expectIllegal(() -> new TelegramDuplicateVideoMediaSourceDescriptor(original, 0L));
    }

    private static DuplicateVideoCoordinatorSnapshot.Item item(long id, int position) {
        return new DuplicateVideoCoordinatorSnapshot.Item(id,
                new TelegramDuplicateVideoMediaSourceDescriptor(new File("video-" + id), 1L), position);
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); } catch (NullPointerException expected) { }
    }

    private static void expectIllegal(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); } catch (IllegalArgumentException expected) { }
    }
}
