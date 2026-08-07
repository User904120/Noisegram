package org.cleargram.storage.telegram;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class TelegramHideChannelPinnedMessageHeaderStorageTest {

    @Test
    public void missingZeroAndUnknownStoredValuesDefaultToDisabled() {
        assertFalse(TelegramHideChannelPinnedMessageHeaderStorage.decodeStoredValue(null));
        assertFalse(TelegramHideChannelPinnedMessageHeaderStorage.decodeStoredValue("0"));
        assertFalse(TelegramHideChannelPinnedMessageHeaderStorage.decodeStoredValue("unknown"));
        assertFalse(TelegramHideChannelPinnedMessageHeaderStorage.decodeStoredValue("2"));
    }

    @Test
    public void oneStoredValueEnablesOnlyThisSetting() {
        assertTrue(TelegramHideChannelPinnedMessageHeaderStorage.decodeStoredValue("1"));
    }

    @Test
    public void persistedKeyIsIndependentFromAdvertisementSettings() {
        assertEquals("hide_channel_pinned_message_header",
                TelegramHideChannelPinnedMessageHeaderStorage.SETTING_KEY);
        assertNotEquals("hide_channel_end_advertisement",
                TelegramHideChannelPinnedMessageHeaderStorage.SETTING_KEY);
        assertNotEquals("hide_fullscreen_video_advertisement",
                TelegramHideChannelPinnedMessageHeaderStorage.SETTING_KEY);
    }

    @Test
    public void explicitWriteUsesOnlyThisIndependentSettingStorage() {
        RecordingWriteOperation writer = new RecordingWriteOperation();
        TelegramHideChannelPinnedMessageHeaderStorage storage =
                new TelegramHideChannelPinnedMessageHeaderStorage(writer);

        storage.setEnabled(true);

        assertEquals(1, writer.calls);
        assertTrue(writer.lastEnabled);
    }

    private static final class RecordingWriteOperation
            implements TelegramHideChannelPinnedMessageHeaderStorage.WriteOperation {
        int calls;
        boolean lastEnabled;

        @Override
        public void write(boolean enabled) {
            calls++;
            lastEnabled = enabled;
        }
    }
}
