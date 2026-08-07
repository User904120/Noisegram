package org.cleargram.integration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.api.DuplicateVideoRuntimeStatus;
import org.cleargram.api.DuplicateVideoStateSnapshot;
import org.cleargram.api.NoiseCore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Integration boundary for the persisted Duplicate Video enabled setting. */
public final class TelegramDuplicateVideoSettingsGateway {

    public interface Callback<T> {
        void onSuccess(T result);

        void onFailure(RuntimeException error);
    }

    interface CoreOperations {
        DuplicateVideoStateSnapshot getState();

        void setEnabled(boolean enabled);
    }

    interface QueueOperations {
        boolean post(Runnable runnable);
    }

    interface CallbackDispatcher {
        void dispatch(Runnable runnable);
    }

    private enum State { LOADING, READY, FAILED }

    private static final class Snapshot {
        private final State state;
        private final boolean enabled;

        private Snapshot(State state, boolean enabled) {
            this.state = state;
            this.enabled = enabled;
        }
    }

    public static final class ManagementState {
        private final boolean ready;
        private final boolean failed;
        private final boolean enabled;

        private ManagementState(boolean ready, boolean failed, boolean enabled) {
            this.ready = ready;
            this.failed = failed;
            this.enabled = enabled;
        }

        public boolean isReady() { return ready; }

        public boolean isFailed() { return failed; }

        public boolean isEnabled() { return enabled; }
    }

    private static final Snapshot LOADING = new Snapshot(State.LOADING, false);
    private static final Snapshot FAILED = new Snapshot(State.FAILED, false);

    private final CoreOperations core;
    private final QueueOperations storageQueue;
    private final CallbackDispatcher callbackDispatcher;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(LOADING);

    public TelegramDuplicateVideoSettingsGateway(NoiseCore core, DispatchQueue storageQueue) {
        this(createCoreOperations(core), createQueueOperations(storageQueue), AndroidUtilities::runOnUIThread);
    }

    TelegramDuplicateVideoSettingsGateway(
            CoreOperations core,
            QueueOperations storageQueue,
            CallbackDispatcher callbackDispatcher
    ) {
        this.core = Objects.requireNonNull(core, "core");
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        this.callbackDispatcher = Objects.requireNonNull(callbackDispatcher, "callbackDispatcher");
    }

    void startInitialLoad() {
        try {
            if (!storageQueue.post(this::refreshFromCore)) {
                snapshot.set(FAILED);
            }
        } catch (RuntimeException error) {
            snapshot.set(FAILED);
            FileLog.e(error);
        }
    }

    /** Returns false before a durable Core state is available or after a read failure. */
    public boolean isDuplicateVideoEnabled() {
        try {
            Snapshot current = snapshot.get();
            return current.state == State.READY && current.enabled;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public ManagementState getManagementState() {
        Snapshot current = snapshot.get();
        return new ManagementState(
                current.state == State.READY,
                current.state == State.FAILED,
                current.state == State.READY && current.enabled);
    }

    public void getManagementState(Callback<ManagementState> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        try {
            if (!storageQueue.post(() -> {
                refreshFromCore();
                dispatchSuccess(callback, terminal, getManagementState());
            })) {
                dispatchFailure(callback, terminal, new IllegalStateException(
                        "Cleargram storage queue rejected Duplicate Video state read"));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    public void setDuplicateVideoEnabled(boolean enabled, Callback<Void> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        try {
            if (!storageQueue.post(() -> {
                try {
                    Snapshot current = snapshot.get();
                    if (current.state != State.READY) {
                        refreshFromCoreOrThrow();
                        current = snapshot.get();
                    }
                    if (current.state != State.READY) {
                        throw new IllegalStateException("Duplicate Video state is not ready");
                    }
                    if (current.enabled != enabled) {
                        core.setEnabled(enabled);
                        publishCoreStateOrThrow();
                    }
                    dispatchSuccess(callback, terminal, null);
                } catch (RuntimeException error) {
                    dispatchFailure(callback, terminal, error);
                }
            })) {
                dispatchFailure(callback, terminal, new IllegalStateException(
                        "Cleargram storage queue rejected Duplicate Video update"));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    private void refreshFromCore() {
        try {
            refreshFromCoreOrThrow();
        } catch (RuntimeException error) {
            snapshot.set(FAILED);
            FileLog.e(error);
        }
    }

    private void refreshFromCoreOrThrow() {
        DuplicateVideoStateSnapshot state = core.getState();
        if (state.getStatus() == DuplicateVideoRuntimeStatus.READY) {
            snapshot.set(new Snapshot(State.READY, true));
        } else if (state.getStatus() == DuplicateVideoRuntimeStatus.DISABLED) {
            snapshot.set(new Snapshot(State.READY, false));
        } else {
            snapshot.set(FAILED);
        }
    }

    private void publishCoreStateOrThrow() {
        refreshFromCoreOrThrow();
        if (snapshot.get().state != State.READY) {
            throw new IllegalStateException("Duplicate Video state is not ready");
        }
    }

    private <T> void dispatchSuccess(Callback<T> callback, AtomicBoolean terminal, T result) {
        if (!terminal.compareAndSet(false, true)) return;
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onSuccess(result);
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private <T> void dispatchFailure(Callback<T> callback, AtomicBoolean terminal, RuntimeException error) {
        if (!terminal.compareAndSet(false, true)) return;
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onFailure(error);
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private static CoreOperations createCoreOperations(NoiseCore core) {
        NoiseCore requiredCore = Objects.requireNonNull(core, "core");
        return new CoreOperations() {
            @Override public DuplicateVideoStateSnapshot getState() { return requiredCore.getDuplicateVideoState(); }
            @Override public void setEnabled(boolean enabled) { requiredCore.setDuplicateVideoEnabled(enabled); }
        };
    }

    private static QueueOperations createQueueOperations(DispatchQueue storageQueue) {
        DispatchQueue requiredQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        return requiredQueue::postRunnable;
    }
}
