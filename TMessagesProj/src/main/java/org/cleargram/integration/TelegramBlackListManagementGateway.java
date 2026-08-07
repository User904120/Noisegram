package org.cleargram.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cleargram.api.BlackListRuleSnapshot;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseCore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Asynchronous platform boundary for Black List management operations. */
public final class TelegramBlackListManagementGateway {

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(RuntimeException error);
    }

    private interface Operation<T> { T execute(); }

    private final NoiseCore core;
    private final DispatchQueue storageQueue;

    TelegramBlackListManagementGateway(NoiseCore core, DispatchQueue storageQueue) {
        this.core = Objects.requireNonNull(core, "core");
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
    }

    public void getRules(Callback<List<BlackListRuleSnapshot>> callback) {
        submit(callback, () -> Collections.unmodifiableList(new ArrayList<>(core.getBlackListRules())));
    }

    public void getAction(Callback<NoiseAction> callback) {
        submit(callback, core::getBlackListAction);
    }

    public void setAction(NoiseAction action, Callback<Void> callback) {
        submit(callback, () -> {
            core.setBlackListAction(action);
            return null;
        });
    }

    public void addRule(String pattern, Callback<BlackListRuleSnapshot> callback) {
        submit(callback, () -> core.addBlackListRule(pattern));
    }

    public void removeRule(String pattern, Callback<Boolean> callback) {
        submit(callback, () -> core.removeBlackListRule(pattern));
    }

    public void setRuleEnabled(String pattern, boolean enabled, Callback<Boolean> callback) {
        submit(callback, () -> core.setBlackListRuleEnabled(pattern, enabled));
    }

    private <T> void submit(Callback<T> callback, Operation<T> operation) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        Runnable runnable = () -> {
            try { dispatchSuccess(callback, terminal, operation.execute()); }
            catch (RuntimeException error) { dispatchFailure(callback, terminal, error); }
        };
        try {
            if (!storageQueue.postRunnable(runnable)) {
                dispatchFailure(callback, terminal, new IllegalStateException(
                        "Cleargram storage queue rejected Black List management operation"));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    private <T> void dispatchSuccess(Callback<T> callback, AtomicBoolean terminal, T result) {
        if (!terminal.compareAndSet(false, true)) { return; }
        AndroidUtilities.runOnUIThread(() -> {
            try { callback.onSuccess(result); } catch (Throwable throwable) { FileLog.e(throwable); }
        });
    }

    private <T> void dispatchFailure(Callback<T> callback, AtomicBoolean terminal, RuntimeException error) {
        if (!terminal.compareAndSet(false, true)) { return; }
        AndroidUtilities.runOnUIThread(() -> {
            try { callback.onFailure(error); } catch (Throwable throwable) { FileLog.e(throwable); }
        });
    }
}
