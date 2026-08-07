package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.DuplicateVideoMatchMode;
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;

/** Storage-port-backed Duplicate Video repository without platform dependencies. */
final class PersistentDuplicateVideoRepository implements DuplicateVideoRepository {

    private final DuplicateVideoStoragePort storagePort;
    private DuplicateVideoRepositorySnapshot state = new DuplicateVideoRepositorySnapshot(
            DuplicateVideoRepositoryReadiness.LOADING, false, null);
    private boolean initialLoadCompleted;

    PersistentDuplicateVideoRepository(DuplicateVideoStoragePort storagePort) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        try {
            storagePort.load(new DuplicateVideoStoragePort.LoadCallback() {
                @Override
                public void onLoaded(DuplicateVideoStorageSettings settings) {
                    publishLoaded(settings);
                }

                @Override
                public void onFailed(Throwable error) {
                    markInitialLoadFailed();
                }
            });
        } catch (RuntimeException error) {
            markInitialLoadFailed();
        }
    }

    @Override
    public synchronized boolean isReady() {
        return state.getReadiness() == DuplicateVideoRepositoryReadiness.READY;
    }

    @Override
    public synchronized DuplicateVideoRepositorySnapshot getState() {
        return state;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        requireReady();
        storagePort.setEnabled(enabled);
        state = ready(enabled, state.getMatchMode());
    }

    @Override
    public synchronized void setMatchMode(DuplicateVideoMatchMode mode) {
        mode = Objects.requireNonNull(mode, "mode");
        requireReady();
        storagePort.setMatchMode(toRawMode(mode));
        state = ready(state.isEnabled(), mode);
    }

    @Override
    public synchronized List<DuplicateVideoClassification> classifyOrdered(
            DuplicateVideoMatchMode mode,
            int keyVersion,
            List<byte[]> matchKeys
    ) {
        requireReady();
        InMemoryDuplicateVideoRepository.validateClassificationArguments(mode, keyVersion, matchKeys);
        try {
            List<DuplicateVideoStorageClassification> stored = storagePort.classifyOrdered(
                    toRawMode(mode), keyVersion, copyKeys(matchKeys));
            List<DuplicateVideoClassification> classifications = mapClassifications(stored, matchKeys.size());
            return Collections.unmodifiableList(classifications);
        } catch (RuntimeException error) {
            markClassificationFailed();
            throw error;
        }
    }

    @Override
    public synchronized void clearHistory() {
        requireReady();
        storagePort.clearHistory();
    }

    private synchronized void publishLoaded(DuplicateVideoStorageSettings settings) {
        if (initialLoadCompleted) {
            return;
        }
        initialLoadCompleted = true;
        try {
            if (settings == null) {
                throw new IllegalArgumentException("settings");
            }
            state = ready(fromRawEnabled(settings.getEnabled()), fromRawMode(settings.getMatchMode()));
        } catch (RuntimeException error) {
            state = failed(null, false);
        }
    }

    private synchronized void markInitialLoadFailed() {
        if (initialLoadCompleted) {
            return;
        }
        initialLoadCompleted = true;
        state = failed(null, false);
    }

    private void markClassificationFailed() {
        state = failed(state.getMatchMode(), state.isEnabled());
    }

    private void requireReady() {
        if (state.getReadiness() != DuplicateVideoRepositoryReadiness.READY) {
            throw new IllegalStateException("Duplicate Video repository is not ready");
        }
    }

    private static DuplicateVideoRepositorySnapshot ready(boolean enabled, DuplicateVideoMatchMode mode) {
        return new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.READY, enabled, mode);
    }

    private static DuplicateVideoRepositorySnapshot failed(
            DuplicateVideoMatchMode mode,
            boolean enabled
    ) {
        return new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.FAILED, enabled, mode);
    }

    private static boolean fromRawEnabled(int enabled) {
        if (enabled == 0) {
            return false;
        }
        if (enabled == 1) {
            return true;
        }
        throw new IllegalArgumentException("invalid enabled value");
    }

    private static DuplicateVideoMatchMode fromRawMode(int mode) {
        if (mode == 1) {
            return DuplicateVideoMatchMode.VIDEO;
        }
        if (mode == 2) {
            return DuplicateVideoMatchMode.VIDEO_AND_TEXT;
        }
        throw new IllegalArgumentException("invalid match mode");
    }

    private static int toRawMode(DuplicateVideoMatchMode mode) {
        if (mode == DuplicateVideoMatchMode.VIDEO) {
            return 1;
        }
        if (mode == DuplicateVideoMatchMode.VIDEO_AND_TEXT) {
            return 2;
        }
        throw new IllegalArgumentException("unsupported match mode");
    }

    private static List<byte[]> copyKeys(List<byte[]> source) {
        List<byte[]> copy = new ArrayList<>(source.size());
        for (byte[] key : source) {
            copy.add(Arrays.copyOf(key, key.length));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<DuplicateVideoClassification> mapClassifications(
            List<DuplicateVideoStorageClassification> stored,
            int expectedSize
    ) {
        if (stored == null || stored.size() != expectedSize) {
            throw new IllegalStateException("invalid storage classification result");
        }
        List<DuplicateVideoClassification> result = new ArrayList<>(stored.size());
        for (DuplicateVideoStorageClassification classification : stored) {
            if (classification == null) {
                throw new IllegalStateException("null storage classification");
            }
            if (classification == DuplicateVideoStorageClassification.FIRST_SEEN) {
                result.add(DuplicateVideoClassification.FIRST_SEEN);
            } else if (classification == DuplicateVideoStorageClassification.DUPLICATE) {
                result.add(DuplicateVideoClassification.DUPLICATE);
            } else {
                throw new IllegalStateException("unknown storage classification");
            }
        }
        return result;
    }
}
