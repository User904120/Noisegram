package org.cleargram.spi;

import java.util.List;

/** Platform-neutral persistence boundary for White List records. */
public interface WhiteListStoragePort {

    void loadRules(LoadCallback callback);

    void insertRule(WhiteListStorageRecord record);

    boolean deleteRule(String canonicalPattern);

    boolean updateRuleEnabled(String canonicalPattern, boolean enabled);

    interface LoadCallback {

        void onLoaded(List<WhiteListStorageRecord> records);

        void onFailed(Throwable error);
    }
}
