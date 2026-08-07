package org.cleargram.storage.telegram;

import android.os.Handler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

/** SQLite boundary for the independent fullscreen video advertisement setting. */
public final class TelegramHideFullscreenVideoAdvertisementStorage {

    public interface LoadCallback {
        void onLoaded(boolean enabled);

        void onFailed(RuntimeException error);
    }

    private enum State {
        NEW,
        LOADING,
        READY,
        FAILED
    }

    static final String SETTING_KEY = "hide_fullscreen_video_advertisement";

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) {
            super("Fullscreen video advertisement setting storage failure", cause);
        }
    }

    interface WriteOperation {
        void write(boolean enabled);
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final WriteOperation testWriteOperation;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramHideFullscreenVideoAdvertisementStorage(
            TelegramWhiteListStorageAdapter whiteListStorageAdapter,
            DispatchQueue storageQueue
    ) {
        database = Objects.requireNonNull(whiteListStorageAdapter, "whiteListStorageAdapter")
                .getDatabase();
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        testWriteOperation = null;
    }

    TelegramHideFullscreenVideoAdvertisementStorage(WriteOperation testWriteOperation) {
        database = null;
        storageQueue = null;
        this.testWriteOperation = Objects.requireNonNull(testWriteOperation, "testWriteOperation");
        state.set(State.READY);
    }

    public void load(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException(
                    "Fullscreen video advertisement setting load is only allowed from NEW state");
        }
        try {
            if (!storageQueue.postRunnable(() -> loadOnStorageQueue(callback))) {
                markInitialLoadFailed();
                callback.onFailed(new StorageFailure(new IllegalStateException(
                        "Cleargram storage queue rejected fullscreen video advertisement setting load")));
            }
        } catch (RuntimeException error) {
            markInitialLoadFailed();
            callback.onFailed(new StorageFailure(error));
        }
    }

    public void setEnabled(boolean enabled) {
        assertReady();
        if (testWriteOperation != null) {
            testWriteOperation.write(enabled);
            return;
        }
        assertOwningQueue();
        try {
            database.upsertSettingValue(SETTING_KEY, enabled ? "1" : "0");
        } catch (SQLiteException | IllegalStateException exception) {
            throw new StorageFailure(exception);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        try {
            boolean enabled = decodeStoredValue(database.loadSettingValue(SETTING_KEY));
            state.set(State.READY);
            callback.onLoaded(enabled);
        } catch (SQLiteException | IllegalStateException exception) {
            markInitialLoadFailed();
            callback.onFailed(new StorageFailure(exception));
        }
    }

    static boolean decodeStoredValue(String value) {
        return "1".equals(value);
    }

    private void markInitialLoadFailed() {
        state.set(State.FAILED);
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("Fullscreen video advertisement setting storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException(
                    "Fullscreen video advertisement setting operation must run on the owning Cleargram storage queue");
        }
    }
}
