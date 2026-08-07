package org.cleargram.storage.telegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CleargramDatabaseFreshInstallDefaultsTest {

    @Test
    public void pendingMarkerCreationDependsOnStorageStateNotSchemaVersion() {
        assertTrue(CleargramDatabase.shouldCreateFreshFilterDefaultsPendingMarker(false, false));
        assertFalse(CleargramDatabase.shouldCreateFreshFilterDefaultsPendingMarker(true, false));
        assertFalse(CleargramDatabase.shouldCreateFreshFilterDefaultsPendingMarker(false, true));
        assertFalse(CleargramDatabase.shouldCreateFreshFilterDefaultsPendingMarker(true, true));
    }

    @Test
    public void freshInstallUsesTheSixExistingEnabledSettingKeys() {
        assertEquals("hide_reactions", TelegramHideReactionsStorage.HIDE_REACTIONS_KEY);
        assertEquals("duplicate_video_enabled", CleargramDatabase.DUPLICATE_VIDEO_ENABLED_KEY);
        assertEquals("hide_channel_end_advertisement",
                TelegramHideChannelEndAdvertisementStorage.HIDE_CHANNEL_END_ADVERTISEMENT_KEY);
        assertEquals("hide_fullscreen_video_advertisement",
                TelegramHideFullscreenVideoAdvertisementStorage.SETTING_KEY);
        assertEquals("hide_channel_pinned_message_header",
                TelegramHideChannelPinnedMessageHeaderStorage.SETTING_KEY);
        assertEquals("message_cta_button_enabled",
                TelegramMessageCtaButtonSettingsStorage.ENABLED_SETTING_KEY);
        assertEquals("fresh_filter_defaults_seed_version",
                CleargramDatabase.FRESH_FILTER_DEFAULTS_SEED_VERSION_KEY);
        assertEquals(".fresh-filter-defaults-v1.pending",
                CleargramDatabase.FRESH_FILTER_DEFAULTS_PENDING_SUFFIX);
    }
}
