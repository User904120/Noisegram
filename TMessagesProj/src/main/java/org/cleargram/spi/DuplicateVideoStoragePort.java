package org.cleargram.spi;

import java.util.List;

/** Platform-neutral durable boundary for Duplicate Video history. */
public interface DuplicateVideoStoragePort {

    void load(LoadCallback callback);

    void setEnabled(boolean enabled);

    void setMatchMode(int matchMode);

    List<DuplicateVideoStorageClassification> classifyOrdered(
            int matchMode,
            int keyVersion,
            List<byte[]> matchKeys
    );

    void clearHistory();

    interface LoadCallback {

        void onLoaded(DuplicateVideoStorageSettings settings);

        void onFailed(Throwable error);
    }
}
