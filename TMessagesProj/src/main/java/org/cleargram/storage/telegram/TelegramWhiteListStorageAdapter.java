package org.cleargram.storage.telegram;

import android.os.Handler;

import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class TelegramWhiteListStorageAdapter implements WhiteListStoragePort {

    private enum State {
        NEW,
        LOADING,
        READY,
        FAILED,
        CLOSED
    }

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) {
            super("Telegram White List storage failure", cause);
        }
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramWhiteListStorageAdapter(File databaseFile, DispatchQueue storageQueue) {
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
        database = new CleargramDatabase(Objects.requireNonNull(databaseFile, "databaseFile"), storageQueue);
    }

    CleargramDatabase getDatabase() {
        return database;
    }

    @Override
    public void loadRules(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException("White List storage load is only allowed from NEW state");
        }
        storageQueue.postRunnable(() -> loadOnStorageQueue(callback));
    }

    @Override
    public void insertRule(WhiteListStorageRecord record) {
        assertOwningQueue();
        assertReady();
        Objects.requireNonNull(record, "record");
        try {
            database.insertWhiteListRow(record.getCanonicalPattern(), record.isEnabled());
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
            return database.deleteWhiteListRow(canonicalPattern);
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
            return database.setWhiteListRowEnabled(canonicalPattern, enabled);
        } catch (SQLiteException exception) {
            state.set(State.FAILED);
            throw new StorageFailure(exception);
        }
    }

    public void close() {
        assertOwningQueue();
        while (true) {
            State current = state.get();
            if (current == State.CLOSED) {
                return;
            }
            if (current == State.LOADING) {
                throw new IllegalStateException("White List storage cannot close while loading");
            }
            if (current == State.NEW) {
                if (state.compareAndSet(State.NEW, State.CLOSED)) {
                    return;
                }
                continue;
            }
            if (current == State.READY || current == State.FAILED) {
                database.close();
                state.set(State.CLOSED);
                return;
            }
            throw new IllegalStateException("Unexpected White List storage state: " + current);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        List<WhiteListStorageRecord> snapshot;
        try {
            database.open();
            List<CleargramDatabase.WhiteListRow> rows = database.loadWhiteListRows();
            List<WhiteListStorageRecord> records = new ArrayList<>(rows.size());
            for (CleargramDatabase.WhiteListRow row : rows) {
                records.add(new WhiteListStorageRecord(row.getCanonicalPattern(), row.isEnabled()));
            }
            snapshot = Collections.unmodifiableList(records);
        } catch (SQLiteException | IllegalArgumentException exception) {
            state.set(State.FAILED);
            callback.onFailed(new StorageFailure(exception));
            return;
        }
        state.set(State.READY);
        callback.onLoaded(snapshot);
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("White List storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("Telegram White List storage operation must run on the owning Cleargram storage queue");
        }
    }
}
