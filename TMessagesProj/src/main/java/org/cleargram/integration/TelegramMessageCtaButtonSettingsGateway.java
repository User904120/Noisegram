package org.cleargram.integration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.storage.telegram.TelegramMessageCtaButtonSettingsStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Management and read-only runtime snapshot boundary for NG-015 settings. */
public final class TelegramMessageCtaButtonSettingsGateway {

    public interface Callback<T> {
        void onSuccess(T result);

        void onFailure(RuntimeException error);
    }

    private enum Lifecycle {
        LOADING,
        READY,
        FAILED
    }

    private static final class Snapshot {
        private final Lifecycle lifecycle;
        private final MessageCtaButtonSettingsState settings;

        private Snapshot(Lifecycle lifecycle, MessageCtaButtonSettingsState settings) {
            this.lifecycle = lifecycle;
            this.settings = settings;
        }
    }

    interface StorageOperations {
        void load(TelegramMessageCtaButtonSettingsStorage.LoadCallback callback);

        void setEnabled(boolean enabled);

        void setAction(MessageCtaButtonAction action);
    }

    interface QueueOperations {
        boolean post(Runnable runnable);
    }

    interface CallbackDispatcher {
        void dispatch(Runnable runnable);
    }

    private static final Snapshot LOADING =
            new Snapshot(Lifecycle.LOADING, MessageCtaButtonSettingsState.DEFAULT);
    private static final Snapshot FAILED =
            new Snapshot(Lifecycle.FAILED, MessageCtaButtonSettingsState.DEFAULT);

    private final StorageOperations storage;
    private final QueueOperations storageQueue;
    private final CallbackDispatcher callbackDispatcher;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(LOADING);

    public TelegramMessageCtaButtonSettingsGateway(
            TelegramMessageCtaButtonSettingsStorage storage,
            DispatchQueue storageQueue
    ) {
        this(createStorageOperations(storage), createQueueOperations(storageQueue),
                AndroidUtilities::runOnUIThread);
    }

    TelegramMessageCtaButtonSettingsGateway(
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
            storage.load(new TelegramMessageCtaButtonSettingsStorage.LoadCallback() {
                @Override
                public void onLoaded(MessageCtaButtonSettingsState settings) {
                    snapshot.set(new Snapshot(Lifecycle.READY, requireSettings(settings)));
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

    /** Lock-free fail-open snapshot for the future Telegram presentation gate. */
    public MessageCtaButtonSettingsState getMessageCtaButtonSettings() {
        try {
            Snapshot current = snapshot.get();
            return current.lifecycle == Lifecycle.READY
                    ? current.settings : MessageCtaButtonSettingsState.DEFAULT;
        } catch (RuntimeException ignored) {
            return MessageCtaButtonSettingsState.DEFAULT;
        }
    }

    public void load(Callback<MessageCtaButtonSettingsState> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        post(() -> {
            Snapshot current = snapshot.get();
            if (current.lifecycle != Lifecycle.READY) {
                dispatchFailure(callback, terminal,
                        new IllegalStateException("Message CTA button settings are not ready"));
                return;
            }
            dispatchSuccess(callback, terminal, current.settings);
        }, callback, terminal, "Cleargram storage queue rejected message CTA button settings load");
    }

    public void setEnabled(boolean enabled, Callback<MessageCtaButtonSettingsState> callback) {
        Objects.requireNonNull(callback, "callback");
        AtomicBoolean terminal = new AtomicBoolean();
        post(() -> {
            try {
                Snapshot current = requireReady();
                if (current.settings.isEnabled() != enabled) {
                    storage.setEnabled(enabled);
                    current = new Snapshot(Lifecycle.READY,
                            new MessageCtaButtonSettingsState(enabled, current.settings.getAction()));
                    snapshot.set(current);
                }
                dispatchSuccess(callback, terminal, current.settings);
            } catch (RuntimeException error) {
                dispatchFailure(callback, terminal, error);
            }
        }, callback, terminal, "Cleargram storage queue rejected message CTA button enabled update");
    }

    public void setAction(
            MessageCtaButtonAction action,
            Callback<MessageCtaButtonSettingsState> callback
    ) {
        Objects.requireNonNull(callback, "callback");
        if (action == null) {
            dispatchFailure(callback, new AtomicBoolean(),
                    new IllegalArgumentException("Message CTA button action must not be null"));
            return;
        }
        AtomicBoolean terminal = new AtomicBoolean();
        post(() -> {
            try {
                Snapshot current = requireReady();
                if (current.settings.getAction() != action) {
                    storage.setAction(action);
                    current = new Snapshot(Lifecycle.READY,
                            new MessageCtaButtonSettingsState(current.settings.isEnabled(), action));
                    snapshot.set(current);
                }
                dispatchSuccess(callback, terminal, current.settings);
            } catch (RuntimeException error) {
                dispatchFailure(callback, terminal, error);
            }
        }, callback, terminal, "Cleargram storage queue rejected message CTA button action update");
    }

    private Snapshot requireReady() {
        Snapshot current = snapshot.get();
        if (current.lifecycle != Lifecycle.READY) {
            throw new IllegalStateException("Message CTA button settings are not ready");
        }
        return current;
    }

    private void post(
            Runnable operation,
            Callback<?> callback,
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

    private void dispatchFailure(Callback<?> callback, AtomicBoolean terminal, RuntimeException error) {
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

    private static MessageCtaButtonSettingsState requireSettings(
            MessageCtaButtonSettingsState settings
    ) {
        return settings != null ? settings : MessageCtaButtonSettingsState.DEFAULT;
    }

    private static StorageOperations createStorageOperations(
            TelegramMessageCtaButtonSettingsStorage storage
    ) {
        TelegramMessageCtaButtonSettingsStorage requiredStorage =
                Objects.requireNonNull(storage, "storage");
        return new StorageOperations() {
            @Override
            public void load(TelegramMessageCtaButtonSettingsStorage.LoadCallback callback) {
                requiredStorage.load(callback);
            }

            @Override
            public void setEnabled(boolean enabled) {
                requiredStorage.setEnabled(enabled);
            }

            @Override
            public void setAction(MessageCtaButtonAction action) {
                requiredStorage.setAction(action);
            }
        };
    }

    private static QueueOperations createQueueOperations(DispatchQueue storageQueue) {
        return Objects.requireNonNull(storageQueue, "storageQueue")::postRunnable;
    }
}
