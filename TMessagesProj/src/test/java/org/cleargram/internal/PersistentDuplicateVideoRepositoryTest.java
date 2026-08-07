package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;

public final class PersistentDuplicateVideoRepositoryTest {

    @Test
    public void beginsLoadingAndPublishesValidSettingsAtomically() {
        FakeStorage storage = new FakeStorage();
        PersistentDuplicateVideoRepository repository = new PersistentDuplicateVideoRepository(storage);
        assertEquals(1, storage.loadCalls);
        assertFalse(repository.isReady());
        expectState(() -> repository.setEnabled(true));
        storage.loaded(new DuplicateVideoStorageSettings(1, 2));
        assertTrue(repository.isReady());
        assertTrue(repository.getState().isEnabled());
        assertEquals(DuplicateVideoMatchMode.VIDEO_AND_TEXT, repository.getState().getMatchMode());
    }

    @Test
    public void invalidInitialLoadAndFailurePublishFailedWithoutDefaults() {
        FakeStorage invalidStorage = new FakeStorage();
        PersistentDuplicateVideoRepository invalid = new PersistentDuplicateVideoRepository(invalidStorage);
        invalidStorage.loaded(new DuplicateVideoStorageSettings(2, 1));
        assertFalse(invalid.isReady());
        assertEquals(DuplicateVideoRepositoryReadiness.FAILED, invalid.getState().getReadiness());
        assertEquals(null, invalid.getState().getMatchMode());

        FakeStorage failedStorage = new FakeStorage();
        PersistentDuplicateVideoRepository failed = new PersistentDuplicateVideoRepository(failedStorage);
        failedStorage.failed(null);
        expectState(failed::clearHistory);
        assertEquals(0, failedStorage.clearCalls);
    }

    @Test
    public void managementWritesAreDurableFirstAndFailuresKeepReadySnapshot() {
        FakeStorage storage = new FakeStorage();
        PersistentDuplicateVideoRepository repository = ready(storage, false, DuplicateVideoMatchMode.VIDEO);
        DuplicateVideoRepositorySnapshot before = repository.getState();
        storage.beforeSetEnabled = () -> assertSame(before, repository.getState());
        repository.setEnabled(true);
        assertTrue(repository.getState().isEnabled());

        DuplicateVideoRepositorySnapshot afterEnabled = repository.getState();
        storage.modeFailure = new IllegalStateException("failure");
        expectState(() -> repository.setMatchMode(DuplicateVideoMatchMode.VIDEO_AND_TEXT));
        assertSame(afterEnabled, repository.getState());
        assertTrue(repository.isReady());
    }

    @Test
    public void classificationMapsInOrderAndFailureTransitionsToFailed() {
        FakeStorage storage = new FakeStorage();
        PersistentDuplicateVideoRepository repository = ready(storage, true, DuplicateVideoMatchMode.VIDEO);
        storage.classifications = Arrays.asList(DuplicateVideoStorageClassification.FIRST_SEEN,
                DuplicateVideoStorageClassification.DUPLICATE);
        assertEquals(Arrays.asList(DuplicateVideoClassification.FIRST_SEEN,
                        DuplicateVideoClassification.DUPLICATE),
                repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                        Arrays.asList(key(1), key(2))));
        storage.classifications = null;
        expectState(() -> repository.classifyOrdered(DuplicateVideoMatchMode.VIDEO, 1,
                Collections.singletonList(key(3))));
        assertEquals(DuplicateVideoRepositoryReadiness.FAILED, repository.getState().getReadiness());
        assertEquals(DuplicateVideoMatchMode.VIDEO, repository.getState().getMatchMode());
    }

    @Test
    public void clearFailureKeepsReadySnapshot() {
        FakeStorage storage = new FakeStorage();
        PersistentDuplicateVideoRepository repository = ready(storage, false, DuplicateVideoMatchMode.VIDEO);
        DuplicateVideoRepositorySnapshot before = repository.getState();
        storage.clearFailure = new IllegalStateException("failure");
        expectState(repository::clearHistory);
        assertSame(before, repository.getState());
        assertTrue(repository.isReady());
    }

    private static PersistentDuplicateVideoRepository ready(
            FakeStorage storage,
            boolean enabled,
            DuplicateVideoMatchMode mode
    ) {
        PersistentDuplicateVideoRepository repository = new PersistentDuplicateVideoRepository(storage);
        storage.loaded(new DuplicateVideoStorageSettings(enabled ? 1 : 0,
                mode == DuplicateVideoMatchMode.VIDEO ? 1 : 2));
        return repository;
    }

    private static byte[] key(int value) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) value);
        return key;
    }

    private static void expectState(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static final class FakeStorage implements DuplicateVideoStoragePort {
        private LoadCallback callback;
        private int loadCalls;
        private int clearCalls;
        private Runnable beforeSetEnabled;
        private RuntimeException modeFailure;
        private RuntimeException clearFailure;
        private List<DuplicateVideoStorageClassification> classifications =
                Collections.singletonList(DuplicateVideoStorageClassification.FIRST_SEEN);

        @Override public void load(LoadCallback callback) { this.callback = callback; loadCalls++; }
        void loaded(DuplicateVideoStorageSettings settings) { callback.onLoaded(settings); }
        void failed(Throwable error) { callback.onFailed(error); }
        @Override public void setEnabled(boolean enabled) {
            if (beforeSetEnabled != null) beforeSetEnabled.run();
        }
        @Override public void setMatchMode(int matchMode) { if (modeFailure != null) throw modeFailure; }
        @Override public List<DuplicateVideoStorageClassification> classifyOrdered(
                int matchMode, int keyVersion, List<byte[]> matchKeys) { return classifications; }
        @Override public void clearHistory() { clearCalls++; if (clearFailure != null) throw clearFailure; }
    }
}
