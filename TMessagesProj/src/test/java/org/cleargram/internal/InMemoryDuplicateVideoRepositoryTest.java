package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoMatchMode;

public final class InMemoryDuplicateVideoRepositoryTest {

    @Test
    public void beginsReadyDisabledInVideoMode() {
        InMemoryDuplicateVideoRepository repository = new InMemoryDuplicateVideoRepository();
        assertTrue(repository.isReady());
        assertEquals(DuplicateVideoRepositoryReadiness.READY, repository.getState().getReadiness());
        assertFalse(repository.getState().isEnabled());
        assertEquals(DuplicateVideoMatchMode.VIDEO, repository.getState().getMatchMode());
    }

    @Test
    public void classifiesBinaryKeysInOrderAndAcrossBatches() {
        InMemoryDuplicateVideoRepository repository = new InMemoryDuplicateVideoRepository();
        byte[] first = key(1);
        assertEquals(Arrays.asList(DuplicateVideoClassification.FIRST_SEEN,
                        DuplicateVideoClassification.DUPLICATE),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                        Arrays.asList(first, key(1))));
        first[0] = 9;
        assertEquals(Collections.singletonList(DuplicateVideoClassification.DUPLICATE),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                        Collections.singletonList(key(1))));
    }

    @Test
    public void separatesHistoryByModeAndKeyVersion() {
        InMemoryDuplicateVideoRepository repository = new InMemoryDuplicateVideoRepository();
        assertEquals(Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1, Collections.singletonList(key(2))));
        assertEquals(Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO_AND_TEXT, 1, Collections.singletonList(key(2))));
        expectArgument(() -> repository.classifyOrdered(
                DuplicateVideoMatchMode.VIDEO, 2, Collections.singletonList(key(2))));
    }

    @Test
    public void managementPreservesHistoryUntilClearAndKeepsStateFields() {
        InMemoryDuplicateVideoRepository repository = new InMemoryDuplicateVideoRepository();
        repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1, Collections.singletonList(key(3)));
        repository.setEnabled(true);
        repository.setMatchMode(DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        assertTrue(repository.getState().isEnabled());
        assertEquals(DuplicateVideoMatchMode.VIDEO_AND_TEXT, repository.getState().getMatchMode());
        assertEquals(Collections.singletonList(DuplicateVideoClassification.DUPLICATE),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1, Collections.singletonList(key(3))));
        repository.clearHistory();
        assertTrue(repository.getState().isEnabled());
        assertEquals(DuplicateVideoMatchMode.VIDEO_AND_TEXT, repository.getState().getMatchMode());
        assertEquals(Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1, Collections.singletonList(key(3))));
    }

    @Test
    public void rejectsInvalidBatchBeforeMutatingHistory() {
        InMemoryDuplicateVideoRepository repository = new InMemoryDuplicateVideoRepository();
        expectNull(() -> repository.classifyOrdered(null, 1, Collections.singletonList(key(4))));
        expectArgument(() -> repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 0,
                Collections.singletonList(key(4))));
        expectArgument(() -> repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                Collections.<byte[]>emptyList()));
        expectArgument(() -> repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                Collections.singletonList(new byte[31])));
        assertEquals(Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1, Collections.singletonList(key(4))));
    }

    private static byte[] key(int value) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) value);
        return key;
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
    }

    private static void expectArgument(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
