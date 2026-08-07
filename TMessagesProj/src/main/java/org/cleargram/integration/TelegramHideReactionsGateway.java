package org.cleargram.integration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.storage.telegram.TelegramHideReactionsStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Integration-layer state and management boundary for Hide Reactions. */
public final class TelegramHideReactionsGateway {

    public interface Callback<T> {
        void onSuccess(T result);

        void onFailure(RuntimeException error);
    }

    private enum State {
        LOADING,
        READY,
        FAILED
    }

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

        public boolean isReady() {
            return ready;
        }

        public boolean isFailed() {
            return failed;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    interface StorageOperations {
        void load(TelegramHideReactionsStorage.LoadCallback callback);

        void setEnabled(boolean enabled);
    }

    interface QueueOperations {
        boolean post(Runnable runnable);
    }

    interface CallbackDispatcher {
        void dispatch(Runnable runnable);
    }

    private static final Snapshot LOADING = new Snapshot(State.LOADING, false);
    private static final Snapshot FAILED = new Snapshot(State.FAILED, false);

    private final StorageOperations storage;
    private final QueueOperations storageQueue;
    private final CallbackDispatcher callbackDispatcher;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(LOADING);

    public TelegramHideReactionsGateway(
            TelegramHideReactionsStorage storage,
            DispatchQueue storageQueue
    ) {
        this(
                createStorageOperations(storage),
                createQueueOperations(storageQueue),
                AndroidUtilities::runOnUIThread);
    }

    TelegramHideReactionsGateway(
            StorageOperations storage,
            QueueOperations storageQueue,
            CallbackDispatcher callbackDispatcher
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        this.callbackDispatcher = Objects.requireNonNull(callbackDispatcher, "callbackDispatcher");
    }

    void startInitialLoad() {
        try {
            storage.load(new TelegramHideReactionsStorage.LoadCallback() {
                @Override
                public void onLoaded(boolean enabled) {
                    snapshot.set(new Snapshot(State.READY, enabled));
                }

                @Override
                public void onFailed(RuntimeException error) {
                    snapshot.set(FAILED);
                }
            });
        } catch (RuntimeException error) {
            snapshot.set(FAILED);
            FileLog.e(error);
        }
    }

    public boolean shouldHideReactions() {
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
        Runnable operation = () -> dispatchManagementState(callback, terminal, getManagementState());
        try {
            if (!storageQueue.post(operation)) {
                dispatchFailure(callback, terminal, new IllegalStateException(
                        "Cleargram storage queue rejected Hide Reactions state read"));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    public void setHideReactionsEnabled(boolean enabled, Callback<Void> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        Runnable operation = () -> {
            try {
                Snapshot current = snapshot.get();
                if (current.state != State.READY) {
                    throw new IllegalStateException("Hide Reactions state is not ready");
                }
                if (current.enabled != enabled) {
                    storage.setEnabled(enabled);
                    snapshot.set(new Snapshot(State.READY, enabled));
                }
                dispatchSuccess(callback, terminal);
            } catch (RuntimeException error) {
                dispatchFailure(callback, terminal, error);
            }
        };
        try {
            if (!storageQueue.post(operation)) {
                dispatchFailure(callback, terminal, new IllegalStateException(
                        "Cleargram storage queue rejected Hide Reactions update"));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    private void dispatchSuccess(Callback<Void> callback, AtomicBoolean terminal) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onSuccess(null);
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private void dispatchManagementState(
            Callback<ManagementState> callback,
            AtomicBoolean terminal,
            ManagementState managementState
    ) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onSuccess(managementState);
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private <T> void dispatchFailure(Callback<T> callback, AtomicBoolean terminal, RuntimeException error) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onFailure(error);
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private static StorageOperations createStorageOperations(TelegramHideReactionsStorage storage) {
        TelegramHideReactionsStorage requiredStorage = Objects.requireNonNull(storage, "storage");
        return new StorageOperations() {
            @Override
            public void load(TelegramHideReactionsStorage.LoadCallback callback) {
                requiredStorage.load(callback);
            }

            @Override
            public void setEnabled(boolean enabled) {
                requiredStorage.setEnabled(enabled);
            }
        };
    }

    private static QueueOperations createQueueOperations(DispatchQueue storageQueue) {
        DispatchQueue requiredStorageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        return requiredStorageQueue::postRunnable;
    }
}
