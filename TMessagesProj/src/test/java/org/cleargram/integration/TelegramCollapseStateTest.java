package org.cleargram.integration;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramCollapseStateTest {

    @Test
    public void groupMembersShareThePrimaryMessageMarker() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        TelegramDecisionContext owner = context(primary, primary, TelegramDecisionContext.PresentationRole.GROUP_OWNER);
        TelegramDecisionContext groupMember = context(primary, member, TelegramDecisionContext.PresentationRole.GROUP_MEMBER);
        TelegramCollapseState state = new TelegramCollapseState();

        state.markExpanded(owner);

        assertTrue(state.isExpanded(groupMember));
    }

    @Test
    public void markerDoesNotExpandAnotherAlbumOrScope() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramCollapseState state = new TelegramCollapseState();
        state.markExpanded(context(primary, primary, TelegramDecisionContext.PresentationRole.GROUP_OWNER));

        assertFalse(state.isExpanded(context(
                TelegramDecisionTestFixtures.message(1, 100L, 0L, 20),
                TelegramDecisionTestFixtures.message(1, 100L, 0L, 21),
                TelegramDecisionContext.PresentationRole.GROUP_MEMBER
        )));
        assertFalse(state.isExpanded(context(
                TelegramDecisionTestFixtures.message(1, 101L, 0L, 10),
                TelegramDecisionTestFixtures.message(1, 101L, 0L, 11),
                TelegramDecisionContext.PresentationRole.GROUP_MEMBER
        )));
        assertFalse(state.isExpanded(context(
                TelegramDecisionTestFixtures.message(2, 100L, 0L, 10),
                TelegramDecisionTestFixtures.message(2, 100L, 0L, 11),
                TelegramDecisionContext.PresentationRole.GROUP_MEMBER
        )));
    }

    @Test
    public void singleMessageMarkerBehaviorIsUnchanged() {
        TelegramDecisionTestFixtures.TestMessage message = TelegramDecisionTestFixtures.message(1, 100L, 7L, 10);
        TelegramDecisionContext context = context(message, message, TelegramDecisionContext.PresentationRole.SINGLE);
        TelegramCollapseState state = new TelegramCollapseState();

        state.markExpanded(context);

        assertTrue(state.isExpanded(context));
        state.remove(context);
        assertFalse(state.isExpanded(context));
    }

    private static TelegramDecisionContext context(
            TelegramDecisionTestFixtures.TestMessage decision,
            TelegramDecisionTestFixtures.TestMessage bound,
            TelegramDecisionContext.PresentationRole role
    ) {
        return new TelegramDecisionContext(decision.currentAccount, decision, bound, TelegramDecisionTestFixtures.cell(bound), role, null);
    }
}
