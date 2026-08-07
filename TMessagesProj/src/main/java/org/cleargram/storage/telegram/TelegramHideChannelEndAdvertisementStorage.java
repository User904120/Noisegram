package org.cleargram.storage.telegram;

import android.os.Handler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

/** SQLite read boundary for the independent channel-end advertisement setting. */
public final class TelegramHideChannelEndAdvertisementStorage {

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

    static final String HIDE_CHANNEL_END_ADVERTISEMENT_KEY =
            "hide_channel_end_advertisement";

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) {
            super("Telegram channel-end advertisement setting storage failure", cause);
        }
    }

    interface WriteOperation {
        void write(boolean enabled);
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final WriteOperation testWriteOperation;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramHideChannelEndAdvertisementStorage(
            TelegramWhiteListStorageAdapter whiteListStorageAdapter,
            DispatchQueue storageQueue
    ) {
        database = Objects.requireNonNull(whiteListStorageAdapter, "whiteListStorageAdapter").getDatabase();
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        testWriteOperation = null;
    }

    TelegramHideChannelEndAdvertisementStorage(WriteOperation testWriteOperation) {
        database = null;
        storageQueue = null;
        this.testWriteOperation = Objects.requireNonNull(testWriteOperation, "testWriteOperation");
        state.set(State.READY);
    }

    public void load(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException(
                    "Channel-end advertisement setting load is only allowed from NEW state");
        }
        try {
            if (!storageQueue.postRunnable(() -> loadOnStorageQueue(callback))) {
                markInitialLoadFailed();
                callback.onFailed(new StorageFailure(new IllegalStateException(
                        "Cleargram storage queue rejected channel-end advertisement setting load")));
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
            database.upsertSettingValue(HIDE_CHANNEL_END_ADVERTISEMENT_KEY, enabled ? "1" : "0");
        } catch (SQLiteException | IllegalStateException exception) {
            throw new StorageFailure(exception);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        try {
            boolean enabled = decodeStoredValue(database.loadSettingValue(
                    HIDE_CHANNEL_END_ADVERTISEMENT_KEY));
            state.set(State.READY);
            callback.onLoaded(enabled);
        } catch (SQLiteException | IllegalStateException exception) {
            markInitialLoadFailed();
            callback.onFailed(new StorageFailure(exception));
        }
    }

    static boolean decodeStoredValue(String value) {
        if (value == null || "0".equals(value)) {
            return false;
        }
        if ("1".equals(value)) {
            return true;
        }
        throw new IllegalStateException("Invalid channel-end advertisement setting value");
    }

    private void markInitialLoadFailed() {
        state.set(State.FAILED);
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("Channel-end advertisement setting storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException(
                    "Channel-end advertisement setting operation must run on the owning Cleargram storage queue");
        }
    }
}
