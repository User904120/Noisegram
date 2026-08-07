package org.cleargram.storage.telegram;

import android.os.Handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

/** Telegram SQLite implementation of the platform-neutral Duplicate Video SPI. */
public final class TelegramDuplicateVideoStorageAdapter implements DuplicateVideoStoragePort {

    private enum State { NEW, LOADING, READY, FAILED }

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) { super("Telegram Duplicate Video storage failure", cause); }
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramDuplicateVideoStorageAdapter(
            TelegramWhiteListStorageAdapter whiteListStorageAdapter,
            DispatchQueue storageQueue
    ) {
        this.database = Objects.requireNonNull(whiteListStorageAdapter, "whiteListStorageAdapter").getDatabase();
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
    }

    @Override
    public void load(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException("Duplicate Video storage load is only allowed from NEW state");
        }
        storageQueue.postRunnable(() -> loadOnStorageQueue(callback));
    }

    @Override
    public void setEnabled(boolean enabled) {
        assertOwningQueue();
        assertReady();
        try {
            database.updateDuplicateVideoEnabled(enabled);
        } catch (SQLiteException | RuntimeException exception) {
            throw new StorageFailure(exception);
        }
    }

    @Override
    public void setMatchMode(int matchMode) {
        assertOwningQueue();
        assertReady();
        if (matchMode != 1 && matchMode != 2) {
            throw new IllegalArgumentException("matchMode");
        }
        try {
            database.updateDuplicateVideoMatchMode(matchMode);
        } catch (SQLiteException | RuntimeException exception) {
            throw new StorageFailure(exception);
        }
    }

    @Override
    public List<DuplicateVideoStorageClassification> classifyOrdered(
            int matchMode, int keyVersion, List<byte[]> matchKeys
    ) {
        assertOwningQueue();
        assertReady();
        validateClassificationArguments(matchMode, keyVersion, matchKeys);
        try {
            List<CleargramDatabase.DuplicateVideoRowClassification> rows =
                    database.classifyDuplicateVideoRows(matchMode, keyVersion, copyKeys(matchKeys),
                            System.currentTimeMillis());
            List<DuplicateVideoStorageClassification> result = new ArrayList<>(rows.size());
            for (CleargramDatabase.DuplicateVideoRowClassification row : rows) {
                result.add(row == CleargramDatabase.DuplicateVideoRowClassification.FIRST_SEEN
                        ? DuplicateVideoStorageClassification.FIRST_SEEN
                        : DuplicateVideoStorageClassification.DUPLICATE);
            }
            return Collections.unmodifiableList(result);
        } catch (SQLiteException | RuntimeException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    @Override
    public void clearHistory() {
        assertOwningQueue();
        assertReady();
        try {
            database.clearDuplicateVideoHistory();
        } catch (SQLiteException | RuntimeException exception) {
            throw new StorageFailure(exception);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        DuplicateVideoStorageSettings storageSettings;
        try {
            CleargramDatabase.DuplicateVideoSettingsRow settings = database.loadDuplicateVideoSettings();
            storageSettings = new DuplicateVideoStorageSettings(
                    settings.getEnabled(), settings.getMatchMode());
        } catch (SQLiteException | RuntimeException exception) {
            state.set(State.FAILED);
            deliverFailure(callback, new StorageFailure(exception));
            return;
        }
        state.set(State.READY);
        try {
            callback.onLoaded(storageSettings);
        } catch (RuntimeException ignored) {
            // A client callback cannot change a completed storage load.
        }
    }

    private void deliverFailure(LoadCallback callback, RuntimeException failure) {
        try {
            callback.onFailed(failure);
        } catch (RuntimeException ignored) {
            // A terminal callback exception cannot trigger another callback.
        }
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("Duplicate Video storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("Duplicate Video storage operation must run on the owning Cleargram storage queue");
        }
    }

    private static void validateClassificationArguments(int matchMode, int keyVersion, List<byte[]> keys) {
        if ((matchMode != 1 && matchMode != 2) || keyVersion != 1 || keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("invalid Duplicate Video classification arguments");
        }
        for (byte[] key : keys) {
            if (key == null || key.length != 32) {
                throw new IllegalArgumentException("invalid Duplicate Video match key");
            }
        }
    }

    private static List<byte[]> copyKeys(List<byte[]> keys) {
        List<byte[]> copy = new ArrayList<>(keys.size());
        for (byte[] key : keys) {
            copy.add(Arrays.copyOf(key, key.length));
        }
        return copy;
    }
}
