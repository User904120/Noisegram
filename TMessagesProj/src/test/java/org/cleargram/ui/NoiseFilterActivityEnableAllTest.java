package org.cleargram.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoiseFilterActivityEnableAllTest {

    @Test
    public void allSixEnabledMasterChecked() {
        NoiseFilterActivity.EnableAllState s = ready();
        assertTrue(s.computeChecked());
    }

    @Test
    public void oneDisabledMasterUnchecked() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.duplicateVideoEnabled = false;
        assertFalse(s.computeChecked());
    }

    @Test
    public void disabledDuplicateVideoIsUncheckedButItsReadyControlCanRemainInteractive() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.duplicateVideoEnabled = false;

        assertFalse(s.computeChecked());
        assertTrue(NoiseFilterActivity.EnableAllState.isChildReady(
                false, false, s.duplicateVideoReady, s.duplicateVideoFailed));
        assertTrue(s.computeEnabled());
    }

    @Test
    public void duplicateVideoRowIsInteractiveOnlyAfterWriteCallback() {
        assertFalse(NoiseFilterActivity.isDuplicateVideoRowInteractive(
                false, true, true, false, false));
        assertTrue(NoiseFilterActivity.isDuplicateVideoRowInteractive(
                false, false, true, false, false));
        assertTrue(NoiseFilterActivity.isDuplicateVideoRowInteractive(
                false, false, true, false, false));
        assertFalse(NoiseFilterActivity.isDuplicateVideoRowInteractive(
                false, false, true, false, true));
    }

    @Test
    public void allDisabledMasterUnchecked() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.hideReactionsEnabled = false;
        s.duplicateVideoEnabled = false;
        s.channelEndAdvertisementEnabled = false;
        s.fullscreenVideoAdvertisementEnabled = false;
        s.channelPinnedMessageHeaderEnabled = false;
        s.ctaEnabled = false;
        assertFalse(s.computeChecked());
    }

    @Test
    public void anyLoadingMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.hideReactionsLoading = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void anyFailedMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.duplicateVideoFailed = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void anyNotReadyMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.channelEndAdvertisementReady = false;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void anyWritingMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.fullscreenVideoAdvertisementUpdating = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void masterUpdatingMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.masterUpdating = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void ctaLoadingMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.ctaLoading = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void ctaWritingMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.ctaWriting = true;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void ctaNotReadyMasterDisabled() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.ctaReady = false;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void toggleWhenPartiallyEnabledTargetTrue() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.hideReactionsEnabled = false;
        boolean target = !s.computeChecked();
        assertTrue(target);
    }

    @Test
    public void toggleWhenFullyEnabledTargetFalse() {
        NoiseFilterActivity.EnableAllState s = ready();
        boolean target = !s.computeChecked();
        assertFalse(target);
    }

    @Test
    public void writesOnlyForDifferingValues() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.hideReactionsEnabled = true;
        s.duplicateVideoEnabled = false;
        s.channelEndAdvertisementEnabled = true;
        s.fullscreenVideoAdvertisementEnabled = false;
        s.channelPinnedMessageHeaderEnabled = true;
        s.ctaEnabled = false;
        boolean target = !s.computeChecked();
        assertTrue(target);
        assertEquals(3, s.computeWriteCount(target));
    }

    @Test
    public void allEnabledToggleWritesAllSix() {
        NoiseFilterActivity.EnableAllState s = ready();
        boolean target = !s.computeChecked();
        assertFalse(target);
        assertEquals(6, s.computeWriteCount(target));
    }

    @Test
    public void partialEnabledWritesCount() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.hideReactionsEnabled = true;
        s.duplicateVideoEnabled = true;
        s.channelEndAdvertisementEnabled = false;
        s.fullscreenVideoAdvertisementEnabled = false;
        s.channelPinnedMessageHeaderEnabled = false;
        s.ctaEnabled = false;
        boolean target = !s.computeChecked();
        assertTrue(target);
        assertEquals(4, s.computeWriteCount(target));
    }

    @Test
    public void enabledRowRequiresAllReady() {
        NoiseFilterActivity.EnableAllState s = ready();
        assertTrue(s.computeEnabled());
        s.hideReactionsReady = false;
        assertFalse(s.computeEnabled());
    }

    @Test
    public void enabledRowRequiresNoFailed() {
        NoiseFilterActivity.EnableAllState s = ready();
        s.channelPinnedMessageHeaderFailed = true;
        assertFalse(s.computeEnabled());
    }

    private static NoiseFilterActivity.EnableAllState ready() {
        NoiseFilterActivity.EnableAllState s = new NoiseFilterActivity.EnableAllState();
        s.hideReactionsEnabled = true;
        s.duplicateVideoEnabled = true;
        s.channelEndAdvertisementEnabled = true;
        s.fullscreenVideoAdvertisementEnabled = true;
        s.channelPinnedMessageHeaderEnabled = true;
        s.ctaEnabled = true;

        s.hideReactionsReady = true;
        s.duplicateVideoReady = true;
        s.channelEndAdvertisementReady = true;
        s.fullscreenVideoAdvertisementReady = true;
        s.channelPinnedMessageHeaderReady = true;
        s.ctaReady = true;
        return s;
    }
}
