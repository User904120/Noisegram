package org.cleargram.ui;

import org.junit.Test;
import org.cleargram.integration.MessageCtaButtonAction;
import org.cleargram.integration.MessageCtaButtonSettingsState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NoiseFilterActivityMessageCtaButtonUiStateTest {

    @Test
    public void loadingAndLoadFailureKeepBothRowsBlocked() {
        NoiseFilterActivity.MessageCtaButtonUiState state =
                new NoiseFilterActivity.MessageCtaButtonUiState();

        state.beginLoad();
        assertFalse(state.canChangeEnabled());
        assertFalse(state.canChangeAction());

        state.failLoad();
        assertNull(state.getLoadedState());
        assertFalse(state.canChangeEnabled());
        assertFalse(state.canChangeAction());
    }

    @Test
    public void successfulLoadPublishesTheWholeSettingsPair() {
        NoiseFilterActivity.MessageCtaButtonUiState state =
                new NoiseFilterActivity.MessageCtaButtonUiState();
        MessageCtaButtonSettingsState loaded =
                new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE);

        state.beginLoad();
        state.completeLoad(loaded);

        assertEquals(loaded, state.getLoadedState());
        assertTrue(state.canChangeEnabled());
        assertTrue(state.canChangeAction());
    }

    @Test
    public void writesBlockBothRowsWithoutOptimisticallyChangingState() {
        NoiseFilterActivity.MessageCtaButtonUiState state = ready(false,
                MessageCtaButtonAction.COLLAPSE);

        state.beginEnabledWrite();
        assertFalse(state.getLoadedState().isEnabled());
        assertFalse(state.canChangeEnabled());
        assertFalse(state.canChangeAction());

        state.failWrite();
        assertFalse(state.getLoadedState().isEnabled());
        assertTrue(state.canChangeEnabled());
        assertFalse(state.canChangeAction());
    }

    @Test
    public void successfulWriteReplacesStateAndPreservesActionFromGatewayResult() {
        NoiseFilterActivity.MessageCtaButtonUiState state = ready(false,
                MessageCtaButtonAction.COLLAPSE);
        MessageCtaButtonSettingsState persisted =
                new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE);

        state.beginEnabledWrite();
        state.completeWrite(persisted);

        assertEquals(persisted, state.getLoadedState());
        assertTrue(state.getLoadedState().isEnabled());
        assertEquals(MessageCtaButtonAction.HIDE, state.getLoadedState().getAction());
        assertTrue(state.canChangeAction());
    }

    @Test
    public void actionWriteKeepsOldValueUntilSuccessfulCallback() {
        NoiseFilterActivity.MessageCtaButtonUiState state = ready(true,
                MessageCtaButtonAction.HIDE);

        state.beginActionWrite();
        assertEquals(MessageCtaButtonAction.HIDE, state.getLoadedState().getAction());
        assertFalse(state.canChangeEnabled());
        assertFalse(state.canChangeAction());

        state.completeWrite(new MessageCtaButtonSettingsState(true,
                MessageCtaButtonAction.COLLAPSE));
        assertEquals(MessageCtaButtonAction.COLLAPSE, state.getLoadedState().getAction());
        assertTrue(state.canChangeAction());
    }

    private static NoiseFilterActivity.MessageCtaButtonUiState ready(
            boolean enabled,
            MessageCtaButtonAction action
    ) {
        NoiseFilterActivity.MessageCtaButtonUiState state =
                new NoiseFilterActivity.MessageCtaButtonUiState();
        state.beginLoad();
        state.completeLoad(new MessageCtaButtonSettingsState(enabled, action));
        return state;
    }
}
