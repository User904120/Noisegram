package org.cleargram.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.cleargram.api.DuplicateVideoBatch;
import org.cleargram.api.DuplicateVideoItem;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoRuntimeStatus;
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;

public final class DuplicateVideoManagerTest {

    @Test
    public void mapsRepositoryStateAndRejectsLoadingStateOperation() {
        FakeRepository repository = new FakeRepository(loading());
        DuplicateVideoManager manager = new DuplicateVideoManager(repository);
        expectState(manager::getState);
        repository.state = ready(false, DuplicateVideoMatchMode.VIDEO);
        assertEquals(DuplicateVideoRuntimeStatus.DISABLED, manager.getState().getStatus());
        repository.state = ready(true, DuplicateVideoMatchMode.VIDEO);
        assertEquals(DuplicateVideoRuntimeStatus.READY, manager.getState().getStatus());
        repository.state = failed(null);
        assertEquals(DuplicateVideoRuntimeStatus.FAILED, manager.getState().getStatus());
    }

    @Test
    public void failOpenGatesReturnEmptyWithoutClassification() {
        DuplicateVideoBatch batch = batch(false, 1, 2);
        for (DuplicateVideoRepositorySnapshot state : Arrays.asList(
                loading(), failed(DuplicateVideoMatchMode.VIDEO),
                ready(false, DuplicateVideoMatchMode.VIDEO),
                ready(true, DuplicateVideoMatchMode.VIDEO_AND_TEXT))) {
            FakeRepository repository = new FakeRepository(state);
            DuplicateVideoManagerResult result = new DuplicateVideoManager(repository).classify(batch);
            assertTrue(result.getHiddenItemIds().isEmpty());
            assertFalse(result.shouldHideWholeMessage());
            assertEquals(0, repository.classifyCalls);
        }
    }

    @Test
    public void mapsDuplicateItemsAndWholeMessageSemantics() {
        FakeRepository repository = new FakeRepository(ready(true, DuplicateVideoMatchMode.VIDEO));
        repository.classifications = Arrays.asList(DuplicateVideoClassification.FIRST_SEEN,
                DuplicateVideoClassification.DUPLICATE);
        DuplicateVideoManagerResult mixed = new DuplicateVideoManager(repository).classify(batch(false, 10, 20));
        assertEquals(Collections.singletonList(20L), mixed.getHiddenItemIds());
        assertFalse(mixed.shouldHideWholeMessage());

        repository.classifications = Arrays.asList(DuplicateVideoClassification.DUPLICATE,
                DuplicateVideoClassification.DUPLICATE);
        DuplicateVideoManagerResult allDuplicates = new DuplicateVideoManager(repository).classify(batch(false, 10, 20));
        assertEquals(Arrays.asList(10L, 20L), allDuplicates.getHiddenItemIds());
        assertTrue(allDuplicates.shouldHideWholeMessage());
        assertFalse(new DuplicateVideoManager(repository).classify(batch(true, 10, 20)).shouldHideWholeMessage());
    }

    @Test
    public void rejectsInvalidRepositoryClassificationAndDelegatesManagementOnlyWhenReady() {
        FakeRepository repository = new FakeRepository(ready(true, DuplicateVideoMatchMode.VIDEO));
        repository.classifications = null;
        expectState(() -> new DuplicateVideoManager(repository).classify(batch(false, 1)));
        repository.classifications = Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN);
        DuplicateVideoManager manager = new DuplicateVideoManager(repository);
        manager.setEnabled(false);
        manager.setMatchMode(DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        manager.clearHistory();
        assertEquals(1, repository.setEnabledCalls);
        assertEquals(1, repository.setModeCalls);
        assertEquals(1, repository.clearCalls);
        expectNull(() -> manager.setMatchMode(null));

        repository.state = failed(DuplicateVideoMatchMode.VIDEO);
        expectState(() -> manager.setEnabled(true));
        assertEquals(1, repository.setEnabledCalls);
    }

    @Test
    public void rejectsNullBatch() {
        expectNull(() -> new DuplicateVideoManager(new FakeRepository(ready(true,
                DuplicateVideoMatchMode.VIDEO))).classify(null));
    }

    @Test
    public void factoriesCreateReadyInMemoryAndLoadingPersistentManagers() {
        DuplicateVideoManager inMemory = DuplicateVideoManager.createInMemory();
        assertEquals(DuplicateVideoRuntimeStatus.DISABLED, inMemory.getState().getStatus());
        assertEquals(DuplicateVideoMatchMode.VIDEO, inMemory.getState().getMatchMode());

        FakeStorage storage = new FakeStorage();
        DuplicateVideoManager persistent = DuplicateVideoManager.createPersistent(storage);
        assertEquals(1, storage.loadCalls);
        expectState(persistent::getState);
        storage.loaded(new DuplicateVideoStorageSettings(1, 1));
        assertEquals(DuplicateVideoRuntimeStatus.READY, persistent.getState().getStatus());
        assertEquals(DuplicateVideoMatchMode.VIDEO, persistent.getState().getMatchMode());
    }

    private static DuplicateVideoBatch batch(boolean otherVisibleMedia, long... ids) {
        DuplicateVideoItem[] items = new DuplicateVideoItem[ids.length];
        for (int index = 0; index < ids.length; index++) {
            byte[] key = new byte[32];
            key[0] = (byte) ids[index];
            items[index] = new DuplicateVideoItem(ids[index], key);
        }
        return new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(items), otherVisibleMedia);
    }

    private static DuplicateVideoRepositorySnapshot loading() {
        return new DuplicateVideoRepositorySnapshot(DuplicateVideoRepositoryReadiness.LOADING, false, null);
    }

    private static DuplicateVideoRepositorySnapshot ready(boolean enabled, DuplicateVideoMatchMode mode) {
        return new DuplicateVideoRepositorySnapshot(DuplicateVideoRepositoryReadiness.READY, enabled, mode);
    }

    private static DuplicateVideoRepositorySnapshot failed(DuplicateVideoMatchMode mode) {
        return new DuplicateVideoRepositorySnapshot(DuplicateVideoRepositoryReadiness.FAILED, false, mode);
    }

    private static void expectState(Runnable runnable) {
        try { runnable.run(); fail("Expected IllegalStateException"); }
        catch (IllegalStateException expected) { }
    }

    private static void expectNull(Runnable runnable) {
        try { runnable.run(); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
    }

    private static final class FakeRepository implements DuplicateVideoRepository {
        private DuplicateVideoRepositorySnapshot state;
        private List<DuplicateVideoClassification> classifications =
                Collections.singletonList(DuplicateVideoClassification.FIRST_SEEN);
        private int classifyCalls;
        private int setEnabledCalls;
        private int setModeCalls;
        private int clearCalls;

        private FakeRepository(DuplicateVideoRepositorySnapshot state) { this.state = state; }
        @Override public boolean isReady() { return state.getReadiness() == DuplicateVideoRepositoryReadiness.READY; }
        @Override public DuplicateVideoRepositorySnapshot getState() { return state; }
        @Override public void setEnabled(boolean enabled) { setEnabledCalls++; }
        @Override public void setMatchMode(DuplicateVideoMatchMode mode) { setModeCalls++; }
        @Override public List<DuplicateVideoClassification> classifyOrdered(
                DuplicateVideoMatchMode mode, int keyVersion, List<byte[]> matchKeys) {
            classifyCalls++;
            return classifications;
        }
        @Override public void clearHistory() { clearCalls++; }
    }

    private static final class FakeStorage implements DuplicateVideoStoragePort {
        private LoadCallback callback;
        private int loadCalls;

        @Override public void load(LoadCallback callback) { this.callback = callback; loadCalls++; }
        private void loaded(DuplicateVideoStorageSettings settings) { callback.onLoaded(settings); }
        @Override public void setEnabled(boolean enabled) { }
        @Override public void setMatchMode(int mode) { }
        @Override public List<DuplicateVideoStorageClassification> classifyOrdered(
                int mode, int keyVersion, List<byte[]> keys) {
            return Collections.nCopies(keys.size(), DuplicateVideoStorageClassification.FIRST_SEEN);
        }
        @Override public void clearHistory() { }
    }
}
