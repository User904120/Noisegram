package org.cleargram.integration;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramDecisionContextTest {

    @Test
    public void singleMessageUsesTheSameCanonicalAndBoundIdentity() {
        TelegramDecisionTestFixtures.TestMessage message = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionContext context = new TelegramDecisionContext(
                1, message, message, TelegramDecisionTestFixtures.cell(message),
                TelegramDecisionContext.PresentationRole.SINGLE, null
        );

        assertTrue(context.hasDecisionIdentity());
        assertTrue(context.isCurrent());
        assertTrue(context.getDecisionMessage() == context.getBoundMessage());
        assertTrue(context.getPresentationRole() == TelegramDecisionContext.PresentationRole.SINGLE);
    }

    @Test
    public void groupOwnerUsesTheCanonicalBoundMessage() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionContext context = new TelegramDecisionContext(
                1, primary, primary, TelegramDecisionTestFixtures.cell(primary),
                TelegramDecisionContext.PresentationRole.GROUP_OWNER, null
        );

        assertTrue(context.isCurrent());
        assertTrue(context.getPresentationRole() == TelegramDecisionContext.PresentationRole.GROUP_OWNER);
    }

    @Test
    public void groupMemberFreshnessUsesBoundMemberInsteadOfCanonicalMessage() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        TelegramDecisionTestFixtures.TestCell cell = TelegramDecisionTestFixtures.cell(member);
        TelegramDecisionContext context = new TelegramDecisionContext(
                1, primary, member, cell, TelegramDecisionContext.PresentationRole.GROUP_MEMBER, null
        );

        assertTrue(context.isCurrent());
        cell.message = primary;
        assertFalse(context.isCurrent());
    }

    @Test
    public void reusedCellForAnotherMemberIsStale() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        TelegramDecisionTestFixtures.TestMessage reused = TelegramDecisionTestFixtures.message(1, 100L, 0L, 12);
        TelegramDecisionTestFixtures.TestCell cell = TelegramDecisionTestFixtures.cell(member);
        TelegramDecisionContext context = new TelegramDecisionContext(
                1, primary, member, cell, TelegramDecisionContext.PresentationRole.GROUP_MEMBER, null
        );

        cell.message = reused;
        assertFalse(context.isCurrent());
    }
}
