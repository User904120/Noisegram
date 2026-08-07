package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatActivityChannelEndAdvertisementSuppressionTest {

    @Test
    public void disabledSettingKeepsTheSingleEndAdvertisementInPresentationFlow() {
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(false, 0, 1));
    }

    @Test
    public void enabledSettingSuppressesConfirmedEndAdvertisementPlacements() {
        assertTrue(ChatActivity.shouldSuppressChannelEndAdvertisement(true, 0, 1));
        assertTrue(ChatActivity.shouldSuppressChannelEndAdvertisement(true, null, 1));
        assertTrue(ChatActivity.shouldSuppressChannelEndAdvertisement(true, 0, 2));
    }

    @Test
    public void enabledSettingDoesNotSuppressInFeedSponsoredMessages() {
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(true, 3, 1));
    }

    @Test
    public void unresolvedPlacementOrEmptyResponseFailsOpen() {
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(true, -1, 1));
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(true, 0, 0));
    }

    @Test
    public void decisionDoesNotClassifyOrModifyOrdinaryMessages() {
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(false, 0, 1));
        assertFalse(ChatActivity.shouldSuppressChannelEndAdvertisement(true, 1, 1));
    }
}
