package org.cleargram.spi;

import org.cleargram.api.NoiseAction;

/** Platform-neutral persistence boundary for Black List records. */
public interface BlackListStoragePort {

    void loadState(LoadCallback callback);

    void insertRule(BlackListStorageRecord record);

    boolean deleteRule(String canonicalPattern);

    boolean updateRuleEnabled(String canonicalPattern, boolean enabled);

    void updateAction(NoiseAction action);

    interface LoadCallback {

        void onLoaded(BlackListStorageState state);

        void onFailed(Throwable error);
    }
}
