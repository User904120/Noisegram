package org.cleargram.storage.telegram;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class TelegramHideChannelEndAdvertisementStorageTest {

    @Test
    public void missingAndZeroStoredValuesDefaultToDisabled() {
        assertFalse(TelegramHideChannelEndAdvertisementStorage.decodeStoredValue(null));
        assertFalse(TelegramHideChannelEndAdvertisementStorage.decodeStoredValue("0"));
    }

    @Test
    public void oneStoredValueEnablesOnlyThisSetting() {
        assertTrue(TelegramHideChannelEndAdvertisementStorage.decodeStoredValue("1"));
    }

    @Test
    public void invalidStoredValueIsRejected() {
        try {
            TelegramHideChannelEndAdvertisementStorage.decodeStoredValue("unexpected");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Durable state validation keeps unknown settings fail-open.
        }
    }

    @Test
    public void explicitWriteUsesOnlyThisIndependentSettingStorage() {
        RecordingWriteOperation writer = new RecordingWriteOperation();
        TelegramHideChannelEndAdvertisementStorage storage =
                new TelegramHideChannelEndAdvertisementStorage(writer);

        storage.setEnabled(true);

        assertEquals(1, writer.calls);
        assertTrue(writer.lastEnabled);
    }

    private static final class RecordingWriteOperation
            implements TelegramHideChannelEndAdvertisementStorage.WriteOperation {
        int calls;
        boolean lastEnabled;

        @Override
        public void write(boolean enabled) {
            calls++;
            lastEnabled = enabled;
        }
    }
}
