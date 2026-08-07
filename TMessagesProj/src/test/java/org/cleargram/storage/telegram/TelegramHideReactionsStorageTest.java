package org.cleargram.storage.telegram;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TelegramHideReactionsStorageTest {

    @Test
    public void runtimeWriteFailureKeepsReadyAndAllowsNextExplicitWrite() {
        RecordingWriteOperation writer = new RecordingWriteOperation();
        TelegramHideReactionsStorage storage = new TelegramHideReactionsStorage(writer);

        writer.failure = new IllegalStateException("write failure");
        try {
            storage.setEnabled(true);
            fail("Expected runtime failure");
        } catch (IllegalStateException expected) {
            // The failed durable operation is propagated without poisoning READY.
        }

        assertTrue(storage.isReady());
        writer.failure = null;
        storage.setEnabled(false);
        assertTrue(storage.isReady());
        assertEquals(2, writer.calls);
        assertFalse(writer.lastEnabled);
    }

    @Test
    public void initialLoadFailureRemainsTerminal() {
        TelegramHideReactionsStorage storage = new TelegramHideReactionsStorage(enabled -> { });

        storage.markInitialLoadFailed();

        assertFalse(storage.isReady());
        try {
            storage.setEnabled(true);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Initial loading failure remains terminal without retry or recovery.
        }
    }

    @Test
    public void missingAndZeroStoredValuesDecodeToFalse() {
        assertFalse(TelegramHideReactionsStorage.decodeStoredValue(null));
        assertFalse(TelegramHideReactionsStorage.decodeStoredValue("0"));
    }

    @Test
    public void oneStoredValueDecodesToTrue() {
        assertTrue(TelegramHideReactionsStorage.decodeStoredValue("1"));
    }

    @Test
    public void invalidStoredValueIsRejected() {
        try {
            TelegramHideReactionsStorage.decodeStoredValue("unexpected");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected durable-state validation.
        }
    }

    private static final class RecordingWriteOperation implements TelegramHideReactionsStorage.WriteOperation {
        RuntimeException failure;
        int calls;
        boolean lastEnabled;

        @Override
        public void write(boolean enabled) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            lastEnabled = enabled;
        }
    }
}
