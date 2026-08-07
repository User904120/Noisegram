package org.cleargram.integration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.storage.telegram.TelegramHideChannelEndAdvertisementStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Integration state and management boundary for the channel-end advertisement setting. */
public final class TelegramHideChannelEndAdvertisementSettingsGateway {

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
        void load(TelegramHideChannelEndAdvertisementStorage.LoadCallback callback);

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

    public TelegramHideChannelEndAdvertisementSettingsGateway(
            TelegramHideChannelEndAdvertisementStorage storage,
            DispatchQueue storageQueue
    ) {
        this(createStorageOperations(storage), createQueueOperations(storageQueue), AndroidUtilities::runOnUIThread);
    }

    TelegramHideChannelEndAdvertisementSettingsGateway(
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
            storage.load(new TelegramHideChannelEndAdvertisementStorage.LoadCallback() {
                @Override
                public void onLoaded(boolean enabled) {
                    snapshot.set(new Snapshot(State.READY, enabled));
                }

                @Override
                public void onFailed(RuntimeException error) {
                    snapshot.set(FAILED);
                    FileLog.e(error);
                }
            });
        } catch (RuntimeException error) {
            snapshot.set(FAILED);
            FileLog.e(error);
        }
    }

    public boolean isHideChannelEndAdvertisementEnabled() {
        try {
            Snapshot current = snapshot.get();
            return current.state == State.READY && current.enabled;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void getManagementState(Callback<ManagementState> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        Runnable operation = () -> dispatchSuccess(callback, terminal, getManagementState());
        post(operation, callback, terminal, "Cleargram storage queue rejected channel-end advertisement state read");
    }

    public void setHideChannelEndAdvertisementEnabled(boolean enabled, Callback<Void> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        Runnable operation = () -> {
            try {
                Snapshot current = snapshot.get();
                if (current.state != State.READY) {
                    throw new IllegalStateException("Channel-end advertisement setting is not ready");
                }
                if (current.enabled != enabled) {
                    storage.setEnabled(enabled);
                    snapshot.set(new Snapshot(State.READY, enabled));
                }
                dispatchSuccess(callback, terminal, null);
            } catch (RuntimeException error) {
                dispatchFailure(callback, terminal, error);
            }
        };
        post(operation, callback, terminal, "Cleargram storage queue rejected channel-end advertisement update");
    }

    private ManagementState getManagementState() {
        Snapshot current = snapshot.get();
        return new ManagementState(
                current.state == State.READY,
                current.state == State.FAILED,
                current.state == State.READY && current.enabled);
    }

    private <T> void post(
            Runnable operation,
            Callback<T> callback,
            AtomicBoolean terminal,
            String rejectedMessage
    ) {
        try {
            if (!storageQueue.post(operation)) {
                dispatchFailure(callback, terminal, new IllegalStateException(rejectedMessage));
            }
        } catch (RuntimeException error) {
            dispatchFailure(callback, terminal, error);
        }
    }

    private <T> void dispatchSuccess(Callback<T> callback, AtomicBoolean terminal, T result) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        callbackDispatcher.dispatch(() -> {
            try {
                callback.onSuccess(result);
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

    private static StorageOperations createStorageOperations(
            TelegramHideChannelEndAdvertisementStorage storage
    ) {
        TelegramHideChannelEndAdvertisementStorage requiredStorage =
                Objects.requireNonNull(storage, "storage");
        return new StorageOperations() {
            @Override
            public void load(TelegramHideChannelEndAdvertisementStorage.LoadCallback callback) {
                requiredStorage.load(callback);
            }

            @Override
            public void setEnabled(boolean enabled) {
                requiredStorage.setEnabled(enabled);
            }
        };
    }

    private static QueueOperations createQueueOperations(DispatchQueue storageQueue) {
        return Objects.requireNonNull(storageQueue, "storageQueue")::postRunnable;
    }
}
