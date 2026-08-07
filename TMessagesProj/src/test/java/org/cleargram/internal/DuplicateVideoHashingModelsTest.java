package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoBatch;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.spi.DuplicateVideoReadableSource;

public final class DuplicateVideoHashingModelsTest {

    @Test
    public void itemRequiresSourceAndPreservesOpaqueIdentity() {
        DuplicateVideoReadableSource source = () -> new ByteArrayInputStream(new byte[0]);
        DuplicateVideoHashingItem item = new DuplicateVideoHashingItem(-7L, source);
        assertEquals(-7L, item.getPresentationItemId());
        assertSame(source, item.getSource());
        expectNull(() -> new DuplicateVideoHashingItem(1L, null));
    }

    @Test
    public void requestValidatesAndDefensivelyOwnsOrderedItems() {
        List<DuplicateVideoHashingItem> items = new ArrayList<>(Arrays.asList(item(2), item(1)));
        DuplicateVideoHashingRequest request = new DuplicateVideoHashingRequest(
                DuplicateVideoMatchMode.VIDEO, 1, items, null, true);
        items.clear();
        assertEquals(Arrays.asList(2L, 1L), ids(request.getItems()));
        assertEquals("", request.getLogicalText());
        assertTrue(request.hasOtherVisibleMedia());
        expectUnsupported(() -> request.getItems().add(item(3)));

        expectNull(() -> new DuplicateVideoHashingRequest(null, 1,
                Collections.singletonList(item(1)), "", false));
        expectIllegal(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 0,
                Collections.singletonList(item(1)), "", false));
        expectIllegal(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, -1,
                Collections.singletonList(item(1)), "", false));
        expectIllegal(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 2,
                Collections.singletonList(item(1)), "", false));
        expectNull(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 1,
                null, "", false));
        expectIllegal(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 1,
                Collections.<DuplicateVideoHashingItem>emptyList(), "", false));
        expectNull(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(item(1), null), "", false));
        expectIllegal(() -> new DuplicateVideoHashingRequest(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(item(1), item(1)), "", false));
    }

    @Test
    public void requestPreservesExactLogicalText() {
        String text = " Caption\t\\n\u00e9 ";
        DuplicateVideoHashingRequest request = new DuplicateVideoHashingRequest(
                DuplicateVideoMatchMode.VIDEO_AND_TEXT, 1, Collections.singletonList(item(1)), text, false);
        assertEquals(text, request.getLogicalText());
        assertFalse(request.hasOtherVisibleMedia());
    }

    @Test
    public void resultDefensivelyOwnsImmutableFailedIdsAndBatchState() {
        expectNull(() -> new DuplicateVideoHashingResult(null, null));
        List<Long> failures = new ArrayList<>(Arrays.asList(4L, 2L));
        DuplicateVideoHashingResult noBatch = new DuplicateVideoHashingResult(null, failures);
        failures.clear();
        assertFalse(noBatch.hasBatch());
        assertEquals(null, noBatch.getBatchOrNull());
        assertEquals(Arrays.asList(4L, 2L), noBatch.getFailedPresentationItemIds());
        expectUnsupported(() -> noBatch.getFailedPresentationItemIds().add(9L));

        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Collections.singletonList(new org.cleargram.api.DuplicateVideoItem(1L, new byte[32])), false);
        DuplicateVideoHashingResult withBatch = new DuplicateVideoHashingResult(batch,
                Collections.<Long>emptyList());
        assertTrue(withBatch.hasBatch());
        assertSame(batch, withBatch.getBatchOrNull());
    }

    private static DuplicateVideoHashingItem item(long id) {
        return new DuplicateVideoHashingItem(id, () -> new ByteArrayInputStream(new byte[] { 1 }));
    }

    private static List<Long> ids(List<DuplicateVideoHashingItem> items) {
        List<Long> result = new ArrayList<>();
        for (DuplicateVideoHashingItem item : items) result.add(item.getPresentationItemId());
        return result;
    }

    private static void expectNull(Runnable action) { expect(NullPointerException.class, action); }
    private static void expectIllegal(Runnable action) { expect(IllegalArgumentException.class, action); }
    private static void expectUnsupported(Runnable action) { expect(UnsupportedOperationException.class, action); }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); fail("Expected " + type.getSimpleName()); }
        catch (Throwable error) { if (!type.isInstance(error)) throw error; }
    }
}
