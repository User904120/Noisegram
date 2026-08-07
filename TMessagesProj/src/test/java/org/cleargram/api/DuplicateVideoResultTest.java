package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class DuplicateVideoResultTest {

    @Test
    public void acceptsAllApprovedListAndWholeMessageCombinations() {
        assertResult(Collections.<Long>emptyList(), false);
        assertResult(Collections.singletonList(1L), false);
        assertResult(Collections.singletonList(1L), true);
        assertResult(Collections.<Long>emptyList(), true);
    }

    @Test
    public void preservesAnyLongValuesAndInputOrder() {
        List<Long> ids = Arrays.asList(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE);
        DuplicateVideoResult result = new DuplicateVideoResult(ids, false);
        assertEquals(ids, result.getHiddenItemIds());
    }

    @Test
    public void rejectsNullListNullIdAndDuplicateId() {
        expectNull(() -> new DuplicateVideoResult(null, false));
        expectNull(() -> new DuplicateVideoResult(Arrays.asList(1L, null), false));
        expectIllegal(() -> new DuplicateVideoResult(Arrays.asList(1L, 1L), false));
    }

    @Test
    public void defensivelyCopiesAndPublishesImmutableIds() {
        List<Long> ids = new ArrayList<>(Arrays.asList(2L, 3L));
        DuplicateVideoResult result = new DuplicateVideoResult(ids, true);
        ids.clear();
        assertEquals(Arrays.asList(2L, 3L), result.getHiddenItemIds());
        assertTrue(result.shouldHideWholeMessage());
        try {
            result.getHiddenItemIds().add(4L);
            fail("Expected immutable ids");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertResult(List<Long> ids, boolean hideWholeMessage) {
        DuplicateVideoResult result = new DuplicateVideoResult(ids, hideWholeMessage);
        assertEquals(ids, result.getHiddenItemIds());
        assertEquals(hideWholeMessage, result.shouldHideWholeMessage());
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); } catch (NullPointerException expected) { }
    }

    private static void expectIllegal(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); } catch (IllegalArgumentException expected) { }
    }
}
