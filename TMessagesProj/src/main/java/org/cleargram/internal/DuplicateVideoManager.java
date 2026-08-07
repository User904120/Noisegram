package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.DuplicateVideoBatch;
import org.cleargram.api.DuplicateVideoItem;
import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.api.DuplicateVideoRuntimeStatus;
import org.cleargram.spi.DuplicateVideoStoragePort;

/** Internal Duplicate Video business boundary for future NoiseCore composition. */
public final class DuplicateVideoManager {

    private final DuplicateVideoRepository repository;

    DuplicateVideoManager(DuplicateVideoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public static DuplicateVideoManager createInMemory() {
        return new DuplicateVideoManager(new InMemoryDuplicateVideoRepository());
    }

    public static DuplicateVideoManager createPersistent(DuplicateVideoStoragePort storagePort) {
        return new DuplicateVideoManager(new PersistentDuplicateVideoRepository(
                Objects.requireNonNull(storagePort, "storagePort")));
    }

    public DuplicateVideoManagerResult classify(DuplicateVideoBatch batch) {
        Objects.requireNonNull(batch, "batch");
        DuplicateVideoRepositorySnapshot state = repository.getState();
        if (state.getReadiness() != DuplicateVideoRepositoryReadiness.READY
                || !state.isEnabled()
                || batch.getMatchMode() != state.getMatchMode()) {
            return emptyResult();
        }
        List<byte[]> matchKeys = new ArrayList<>(batch.getItems().size());
        for (DuplicateVideoItem item : batch.getItems()) {
            matchKeys.add(item.getMatchKey());
        }
        List<DuplicateVideoClassification> classifications = repository.classifyOrdered(
                batch.getMatchMode(), batch.getKeyVersion(), matchKeys);
        validateClassifications(classifications, batch.getItems().size());
        List<Long> hiddenIds = new ArrayList<>();
        boolean hasFirstSeen = false;
        for (int index = 0; index < classifications.size(); index++) {
            if (classifications.get(index) == DuplicateVideoClassification.DUPLICATE) {
                hiddenIds.add(batch.getItems().get(index).getPresentationItemId());
            } else {
                hasFirstSeen = true;
            }
        }
        return new DuplicateVideoManagerResult(
                hiddenIds, !hasFirstSeen && !batch.hasOtherVisibleMedia());
    }

    public DuplicateVideoManagerState getState() {
        DuplicateVideoRepositorySnapshot state = repository.getState();
        if (state.getReadiness() == DuplicateVideoRepositoryReadiness.LOADING) {
            throw new IllegalStateException("Duplicate Video repository is loading");
        }
        if (state.getReadiness() == DuplicateVideoRepositoryReadiness.FAILED) {
            return new DuplicateVideoManagerState(DuplicateVideoRuntimeStatus.FAILED, state.getMatchMode());
        }
        return new DuplicateVideoManagerState(
                state.isEnabled() ? DuplicateVideoRuntimeStatus.READY : DuplicateVideoRuntimeStatus.DISABLED,
                state.getMatchMode());
    }

    public void setEnabled(boolean enabled) {
        requireReady();
        repository.setEnabled(enabled);
    }

    public void setMatchMode(DuplicateVideoMatchMode mode) {
        Objects.requireNonNull(mode, "mode");
        requireReady();
        repository.setMatchMode(mode);
    }

    public void clearHistory() {
        requireReady();
        repository.clearHistory();
    }

    private void requireReady() {
        if (repository.getState().getReadiness() != DuplicateVideoRepositoryReadiness.READY) {
            throw new IllegalStateException("Duplicate Video repository is not ready");
        }
    }

    private static DuplicateVideoManagerResult emptyResult() {
        return new DuplicateVideoManagerResult(Collections.<Long>emptyList(), false);
    }

    private static void validateClassifications(
            List<DuplicateVideoClassification> classifications,
            int expectedSize
    ) {
        if (classifications == null || classifications.size() != expectedSize) {
            throw new IllegalStateException("invalid classification result");
        }
        for (DuplicateVideoClassification classification : classifications) {
            if (classification == null) {
                throw new IllegalStateException("null classification");
            }
        }
    }
}
