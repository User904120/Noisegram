package org.cleargram.storage.telegram;

import android.os.Handler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.cleargram.integration.MessageCtaButtonAction;
import org.cleargram.integration.MessageCtaButtonSettingsState;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.DispatchQueue;

/** SQLite boundary for the independent NG-015 message CTA setting pair. */
public final class TelegramMessageCtaButtonSettingsStorage {

    public interface LoadCallback {
        void onLoaded(MessageCtaButtonSettingsState state);

        void onFailed(RuntimeException error);
    }

    private enum State {
        NEW,
        LOADING,
        READY,
        FAILED
    }

    public static final String ENABLED_SETTING_KEY = "message_cta_button_enabled";
    public static final String ACTION_SETTING_KEY = "message_cta_button_action";

    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) {
            super("Message CTA button setting storage failure", cause);
        }
    }

    private final DispatchQueue storageQueue;
    private final CleargramDatabase database;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public TelegramMessageCtaButtonSettingsStorage(
            TelegramWhiteListStorageAdapter whiteListStorageAdapter,
            DispatchQueue storageQueue
    ) {
        database = Objects.requireNonNull(whiteListStorageAdapter, "whiteListStorageAdapter")
                .getDatabase();
        this.storageQueue = Objects.requireNonNull(storageQueue, "storageQueue");
    }

    public void load(LoadCallback callback) {
        Objects.requireNonNull(callback, "callback");
        if (!state.compareAndSet(State.NEW, State.LOADING)) {
            throw new IllegalStateException("Message CTA button settings load is only allowed from NEW state");
        }
        try {
            if (!storageQueue.postRunnable(() -> loadOnStorageQueue(callback))) {
                state.set(State.FAILED);
                callback.onFailed(new StorageFailure(new IllegalStateException(
                        "Cleargram storage queue rejected message CTA button settings load")));
            }
        } catch (RuntimeException error) {
            state.set(State.FAILED);
            callback.onFailed(new StorageFailure(error));
        }
    }

    public void setEnabled(boolean enabled) {
        assertReady();
        assertOwningQueue();
        try {
            database.upsertSettingValue(ENABLED_SETTING_KEY, enabled ? "1" : "0");
        } catch (SQLiteException | IllegalStateException exception) {
            throw new StorageFailure(exception);
        }
    }

    public void setAction(MessageCtaButtonAction action) {
        assertReady();
        assertOwningQueue();
        if (action == null) {
            throw new IllegalArgumentException("Message CTA button action must not be null");
        }
        try {
            database.upsertSettingValue(ACTION_SETTING_KEY, encodeAction(action));
        } catch (SQLiteException | IllegalStateException exception) {
            throw new StorageFailure(exception);
        }
    }

    private void loadOnStorageQueue(LoadCallback callback) {
        assertOwningQueue();
        try {
            MessageCtaButtonSettingsState loaded = decodeState(
                    database.loadSettingValue(ENABLED_SETTING_KEY),
                    database.loadSettingValue(ACTION_SETTING_KEY));
            state.set(State.READY);
            callback.onLoaded(loaded);
        } catch (SQLiteException | IllegalStateException exception) {
            state.set(State.FAILED);
            callback.onFailed(new StorageFailure(exception));
        }
    }

    public static MessageCtaButtonSettingsState decodeState(String enabledValue, String actionValue) {
        if (actionValue != null && decodeActionOrNull(actionValue) == null) {
            return MessageCtaButtonSettingsState.DEFAULT;
        }
        return new MessageCtaButtonSettingsState("1".equals(enabledValue), decodeAction(actionValue));
    }

    public static MessageCtaButtonAction decodeAction(String value) {
        MessageCtaButtonAction decoded = decodeActionOrNull(value);
        return decoded != null ? decoded : MessageCtaButtonAction.COLLAPSE;
    }

    public static String encodeAction(MessageCtaButtonAction action) {
        if (action == MessageCtaButtonAction.HIDE) {
            return "1";
        }
        if (action == MessageCtaButtonAction.COLLAPSE) {
            return "2";
        }
        throw new IllegalArgumentException("Unsupported message CTA button action: " + action);
    }

    private static MessageCtaButtonAction decodeActionOrNull(String value) {
        if (value == null || "2".equals(value)) {
            return MessageCtaButtonAction.COLLAPSE;
        }
        if ("1".equals(value)) {
            return MessageCtaButtonAction.HIDE;
        }
        return null;
    }

    private void assertReady() {
        if (state.get() != State.READY) {
            throw new IllegalStateException("Message CTA button settings storage is not READY");
        }
    }

    private void assertOwningQueue() {
        Handler handler = storageQueue.getHandler();
        if (handler == null || Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException(
                    "Message CTA button settings operation must run on the owning Cleargram storage queue");
        }
    }
}
