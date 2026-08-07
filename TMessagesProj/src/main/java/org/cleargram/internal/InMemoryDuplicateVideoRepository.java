package org.cleargram.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.cleargram.api.DuplicateVideoMatchMode;

/** Deterministic in-memory Duplicate Video history repository. */
final class InMemoryDuplicateVideoRepository implements DuplicateVideoRepository {

    private static final int SUPPORTED_KEY_VERSION = 1;

    private DuplicateVideoRepositorySnapshot state = new DuplicateVideoRepositorySnapshot(
            DuplicateVideoRepositoryReadiness.READY, false, DuplicateVideoMatchMode.VIDEO);
    private final Set<HistoryKey> history = new HashSet<>();

    @Override
    public synchronized boolean isReady() {
        return true;
    }

    @Override
    public synchronized DuplicateVideoRepositorySnapshot getState() {
        return state;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        state = new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.READY, enabled, state.getMatchMode());
    }

    @Override
    public synchronized void setMatchMode(DuplicateVideoMatchMode mode) {
        state = new DuplicateVideoRepositorySnapshot(
                DuplicateVideoRepositoryReadiness.READY, state.isEnabled(),
                Objects.requireNonNull(mode, "mode"));
    }

    @Override
    public synchronized List<DuplicateVideoClassification> classifyOrdered(
            DuplicateVideoMatchMode mode,
            int keyVersion,
            List<byte[]> matchKeys
    ) {
        validateClassificationArguments(mode, keyVersion, matchKeys);
        Set<HistoryKey> stagedHistory = new HashSet<>(history);
        List<DuplicateVideoClassification> classifications = new ArrayList<>(matchKeys.size());
        for (byte[] matchKey : matchKeys) {
            HistoryKey key = new HistoryKey(mode, keyVersion, matchKey);
            classifications.add(stagedHistory.add(key)
                    ? DuplicateVideoClassification.FIRST_SEEN
                    : DuplicateVideoClassification.DUPLICATE);
        }
        history.addAll(stagedHistory);
        return Collections.unmodifiableList(classifications);
    }

    @Override
    public synchronized void clearHistory() {
        history.clear();
    }

    static void validateClassificationArguments(
            DuplicateVideoMatchMode mode,
            int keyVersion,
            List<byte[]> matchKeys
    ) {
        Objects.requireNonNull(mode, "mode");
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        if (keyVersion != SUPPORTED_KEY_VERSION) {
            throw new IllegalArgumentException("unsupported keyVersion");
        }
        Objects.requireNonNull(matchKeys, "matchKeys");
        if (matchKeys.isEmpty()) {
            throw new IllegalArgumentException("matchKeys must not be empty");
        }
        for (byte[] matchKey : matchKeys) {
            Objects.requireNonNull(matchKey, "matchKeys must not contain null");
            if (matchKey.length != 32) {
                throw new IllegalArgumentException("matchKey must contain 32 bytes");
            }
        }
    }

    private static final class HistoryKey {

        private final DuplicateVideoMatchMode mode;
        private final int keyVersion;
        private final byte[] matchKey;

        private HistoryKey(DuplicateVideoMatchMode mode, int keyVersion, byte[] matchKey) {
            this.mode = mode;
            this.keyVersion = keyVersion;
            this.matchKey = Arrays.copyOf(matchKey, matchKey.length);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof HistoryKey)) {
                return false;
            }
            HistoryKey other = (HistoryKey) object;
            return keyVersion == other.keyVersion && mode == other.mode
                    && Arrays.equals(matchKey, other.matchKey);
        }

        @Override
        public int hashCode() {
            int result = mode.hashCode();
            result = 31 * result + keyVersion;
            return 31 * result + Arrays.hashCode(matchKey);
        }
    }
}
