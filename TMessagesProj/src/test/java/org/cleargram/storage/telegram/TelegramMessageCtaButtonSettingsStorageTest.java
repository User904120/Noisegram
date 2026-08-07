package org.cleargram.storage.telegram;

import org.junit.Test;
import org.cleargram.integration.MessageCtaButtonAction;
import org.cleargram.integration.MessageCtaButtonSettingsState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class TelegramMessageCtaButtonSettingsStorageTest {

    @Test
    public void actionMappingUsesIndependentDurableValues() {
        assertEquals("1", TelegramMessageCtaButtonSettingsStorage.encodeAction(MessageCtaButtonAction.HIDE));
        assertEquals("2", TelegramMessageCtaButtonSettingsStorage.encodeAction(MessageCtaButtonAction.COLLAPSE));
        assertEquals(MessageCtaButtonAction.HIDE,
                TelegramMessageCtaButtonSettingsStorage.decodeAction("1"));
        assertEquals(MessageCtaButtonAction.COLLAPSE,
                TelegramMessageCtaButtonSettingsStorage.decodeAction("2"));
    }

    @Test
    public void missingAndMalformedValuesFailOpenToDefaultState() {
        MessageCtaButtonSettingsState missing =
                TelegramMessageCtaButtonSettingsStorage.decodeState(null, null);
        assertFalse(missing.isEnabled());
        assertEquals(MessageCtaButtonAction.COLLAPSE, missing.getAction());

        MessageCtaButtonSettingsState malformed =
                TelegramMessageCtaButtonSettingsStorage.decodeState("1", "unexpected");
        assertFalse(malformed.isEnabled());
        assertEquals(MessageCtaButtonAction.COLLAPSE, malformed.getAction());
    }
}
