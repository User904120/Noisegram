package org.cleargram.internal;

import java.util.List;

import org.cleargram.api.DuplicateVideoMatchMode;

interface DuplicateVideoRepository {

    boolean isReady();

    DuplicateVideoRepositorySnapshot getState();

    void setEnabled(boolean enabled);

    void setMatchMode(DuplicateVideoMatchMode mode);

    List<DuplicateVideoClassification> classifyOrdered(
            DuplicateVideoMatchMode mode,
            int keyVersion,
            List<byte[]> matchKeys
    );

    void clearHistory();
}
