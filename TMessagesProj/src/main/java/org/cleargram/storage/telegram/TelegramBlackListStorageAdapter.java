package org.cleargram.storage.telegram;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.api.NoiseAction;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageRecord;
import org.cleargram.spi.BlackListStorageState;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

/** Telegram SQLite implementation of the platform-neutral Black List storage port. */
public final class TelegramBlackListStorageAdapter implements BlackListStoragePort {

    private enum State { NEW, LOADING, READY, FAILED, CLOSED }

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) { super("Telegram Black List storage failure", cause); }
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramBlackListStorageAdapter(
            TelegramWhiteListStorageAdapter whiteListStorageAdapter,
            DispatchQueue storageQueue
    ) {
        this.database = Objects.requireNonNull(whiteListStorageAdapter, "whiteListStorageAdapter").getDatabase();
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
    }

    @Override
    public void loadState(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException("Black List storage load is only allowed from NEW state");
        }
        storageQueue.postRunnable(() -> loadOnStorageQueue(callback));
    }

    @Override
    public void insertRule(BlackListStorageRecord record) {
        assertOwningQueue();
        assertReady();
        Objects.requireNonNull(record, "record");
        try {
            database.insertBlackListRow(record.getCanonicalPattern(), record.getAction(), record.isEnabled());
        } catch (SQLiteException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    @Override
    public boolean deleteRule(String canonicalPattern) {
        assertOwningQueue();
        assertReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        try {
            return database.deleteBlackListRow(canonicalPattern);
        } catch (SQLiteException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    @Override
    public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) {
        assertOwningQueue();
        assertReady();
        Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        try {
            return database.setBlackListRowEnabled(canonicalPattern, enabled);
        } catch (SQLiteException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    @Override
    public void updateAction(NoiseAction action) {
        assertOwningQueue();
        assertReady();
        Objects.requireNonNull(action, "action");
        try {
            database.updateBlackListAction(action);
        } catch (SQLiteException | IllegalStateException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        BlackListStorageState storageState;
        try {
            NoiseAction action = database.loadBlackListAction();
            List<CleargramDatabase.BlackListRow> rows = database.loadBlackListRows();
            List<BlackListStorageRecord> records = new ArrayList<>(rows.size());
            for (CleargramDatabase.BlackListRow row : rows) {
                records.add(new BlackListStorageRecord(row.getCanonicalPattern(), row.getAction(), row.isEnabled()));
            }
            storageState = new BlackListStorageState(action, records);
        } catch (SQLiteException | RuntimeException exception) {
            state.set(State.FAILED);
            callback.onFailed(new StorageFailure(exception));
            return;
        }
        state.set(State.READY);
        callback.onLoaded(storageState);
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("Black List storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("Telegram Black List storage operation must run on the owning Cleargram storage queue");
        }
    }
}
