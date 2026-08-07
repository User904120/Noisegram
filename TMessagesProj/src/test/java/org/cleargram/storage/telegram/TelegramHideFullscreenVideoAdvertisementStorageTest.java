package org.cleargram.storage.telegram;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class TelegramHideFullscreenVideoAdvertisementStorageTest {

    @Test
    public void missingZeroAndUnknownStoredValuesDefaultToDisabled() {
        assertFalse(TelegramHideFullscreenVideoAdvertisementStorage.decodeStoredValue(null));
        assertFalse(TelegramHideFullscreenVideoAdvertisementStorage.decodeStoredValue("0"));
        assertFalse(TelegramHideFullscreenVideoAdvertisementStorage.decodeStoredValue("unknown"));
        assertFalse(TelegramHideFullscreenVideoAdvertisementStorage.decodeStoredValue("2"));
    }

    @Test
    public void oneStoredValueEnablesOnlyThisSetting() {
        assertTrue(TelegramHideFullscreenVideoAdvertisementStorage.decodeStoredValue("1"));
    }

    @Test
    public void persistedKeyIsIndependentFromChannelEndAdvertisement() {
        assertEquals("hide_fullscreen_video_advertisement",
                TelegramHideFullscreenVideoAdvertisementStorage.SETTING_KEY);
        assertNotEquals("hide_channel_end_advertisement",
                TelegramHideFullscreenVideoAdvertisementStorage.SETTING_KEY);
    }

    @Test
    public void explicitWriteUsesOnlyThisIndependentSettingStorage() {
        RecordingWriteOperation writer = new RecordingWriteOperation();
        TelegramHideFullscreenVideoAdvertisementStorage storage =
                new TelegramHideFullscreenVideoAdvertisementStorage(writer);

        storage.setEnabled(true);

        assertEquals(1, writer.calls);
        assertTrue(writer.lastEnabled);
    }

    private static final class RecordingWriteOperation
            implements TelegramHideFullscreenVideoAdvertisementStorage.WriteOperation {
        int calls;
        boolean lastEnabled;

        @Override
        public void write(boolean enabled) {
            calls++;
            lastEnabled = enabled;
        }
    }
}
