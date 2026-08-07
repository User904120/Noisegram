package org.cleargram.integration;

import org.junit.Test;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class TelegramDecisionApplierTest {

    private final TelegramDecisionApplier applier = new TelegramDecisionApplier(() -> "collapsed");

    @Test
    public void singleAndGroupOwnerCollapseToCompactButMemberIsHidden() {
        TelegramDecisionTestFixtures.TestCell single = apply(NoiseAction.COLLAPSE, roleContext(TelegramDecisionContext.PresentationRole.SINGLE));
        TelegramDecisionTestFixtures.TestCell owner = apply(NoiseAction.COLLAPSE, roleContext(TelegramDecisionContext.PresentationRole.GROUP_OWNER));
        TelegramDecisionTestFixtures.TestCell member = apply(NoiseAction.COLLAPSE, roleContext(TelegramDecisionContext.PresentationRole.GROUP_MEMBER));

        assertTrue(single.compact);
        assertTrue(owner.compact);
        assertTrue(member.hidden);
        assertNull(member.expansionCallback);
    }

    @Test
    public void hideAndAllowUseTheExistingCellPresentationMechanicsForEveryRole() {
        for (TelegramDecisionContext.PresentationRole role : TelegramDecisionContext.PresentationRole.values()) {
            TelegramDecisionTestFixtures.TestCell hidden = apply(NoiseAction.HIDE, roleContext(role));
            assertTrue(hidden.hidden);

            TelegramDecisionTestFixtures.TestCell normal = apply(NoiseAction.ALLOW, roleContext(role));
            assertFalse(normal.compact);
            assertFalse(normal.hidden);
            assertEquals(1, normal.resetCalls);
        }
    }

    @Test
    public void expandedGroupRestoresOwnerAndMemberWithoutCompactPresentation() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        TelegramCollapseState state = new TelegramCollapseState();
        TelegramDecisionContext owner = context(primary, primary, TelegramDecisionContext.PresentationRole.GROUP_OWNER, null);
        state.markExpanded(owner);

        TelegramDecisionTestFixtures.TestCell ownerCell = (TelegramDecisionTestFixtures.TestCell) owner.getPresentationTarget();
        applier.apply(new NoiseDecision(NoiseAction.COLLAPSE), owner, state);
        TelegramDecisionContext memberContext = context(primary, member, TelegramDecisionContext.PresentationRole.GROUP_MEMBER, null);
        TelegramDecisionTestFixtures.TestCell memberCell = (TelegramDecisionTestFixtures.TestCell) memberContext.getPresentationTarget();
        applier.apply(new NoiseDecision(NoiseAction.COLLAPSE), memberContext, state);

        assertFalse(ownerCell.compact);
        assertFalse(memberCell.hidden);
        assertEquals(1, ownerCell.resetCalls);
        assertEquals(1, memberCell.resetCalls);
    }

    @Test
    public void staleBoundContextFailsOpen() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        TelegramDecisionContext context = context(primary, member, TelegramDecisionContext.PresentationRole.GROUP_MEMBER, null);
        TelegramDecisionTestFixtures.TestCell cell = (TelegramDecisionTestFixtures.TestCell) context.getPresentationTarget();
        cell.message = TelegramDecisionTestFixtures.message(1, 100L, 0L, 12);

        applier.apply(new NoiseDecision(NoiseAction.COLLAPSE), context, new TelegramCollapseState());

        assertFalse(cell.compact);
        assertFalse(cell.hidden);
        assertEquals(1, cell.resetCalls);
    }

    @Test
    public void ownerExpansionMarksCanonicalIdentityAndCallsTheUnitCallbackOnce() {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage member = TelegramDecisionTestFixtures.message(1, 100L, 0L, 11);
        int[] callbackCalls = new int[1];
        TelegramDecisionContext owner = context(
                primary,
                primary,
                TelegramDecisionContext.PresentationRole.GROUP_OWNER,
                () -> callbackCalls[0]++
        );
        TelegramCollapseState state = new TelegramCollapseState();
        TelegramDecisionTestFixtures.TestCell ownerCell = (TelegramDecisionTestFixtures.TestCell) owner.getPresentationTarget();

        applier.apply(new NoiseDecision(NoiseAction.COLLAPSE), owner, state);
        assertTrue(ownerCell.compact);
        ownerCell.expansionCallback.run();

        assertEquals(1, callbackCalls[0]);
        assertTrue(state.isExpanded(context(primary, member, TelegramDecisionContext.PresentationRole.GROUP_MEMBER, null)));
        assertEquals(1, ownerCell.resetCalls);
    }

    private TelegramDecisionTestFixtures.TestCell apply(NoiseAction action, TelegramDecisionContext context) {
        TelegramDecisionTestFixtures.TestCell cell = (TelegramDecisionTestFixtures.TestCell) context.getPresentationTarget();
        applier.apply(new NoiseDecision(action), context, new TelegramCollapseState());
        return cell;
    }

    private static TelegramDecisionContext roleContext(TelegramDecisionContext.PresentationRole role) {
        TelegramDecisionTestFixtures.TestMessage primary = TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestMessage bound = role == TelegramDecisionContext.PresentationRole.GROUP_MEMBER
                ? TelegramDecisionTestFixtures.message(1, 100L, 0L, 11)
                : primary;
        return context(primary, bound, role, null);
    }

    private static TelegramDecisionContext context(
            TelegramDecisionTestFixtures.TestMessage decision,
            TelegramDecisionTestFixtures.TestMessage bound,
            TelegramDecisionContext.PresentationRole role,
            Runnable callback
    ) {
        return new TelegramDecisionContext(decision.currentAccount, decision, bound, TelegramDecisionTestFixtures.cell(bound), role, callback);
    }
}
