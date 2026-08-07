package org.cleargram.integration;

import org.junit.Test;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class TelegramMessageCtaPresentationPolicyTest {

    @Test
    public void disabledAndNonMatchingMessagesDoNotOverrideCoreDecision() {
        MessageCtaClassificationInput matching = inputWith(singleUrlRow());
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, matching,
                new MessageCtaButtonSettingsState(false, MessageCtaButtonAction.HIDE)));
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, inputWith(null),
                new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE)));
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, matching, null));
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, matching,
                MessageCtaButtonSettingsState.DEFAULT));
    }

    @Test
    public void enabledMatchingMessageMapsEachSupportedAction() {
        MessageCtaClassificationInput matching = inputWith(singleUrlRow());
        assertEquals(NoiseAction.HIDE, resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                matching, new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE)));
        assertEquals(NoiseAction.COLLAPSE, resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                matching, new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.COLLAPSE)));
    }

    @Test
    public void sponsoredAndGroupedMessagesFailOpen() {
        MessageCtaButtonSettingsState enabled =
                new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE);
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                new MessageCtaClassificationInput(singleUrlRow(), true, false), enabled));
        assertNull(resolve(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                new MessageCtaClassificationInput(singleUrlRow(), false, true), enabled));
    }

    @Test
    public void terminalAndFailOpenCoreOutcomesKeepTheirOriginalDecision() {
        MessageCtaClassificationInput matching = inputWith(singleUrlRow());
        MessageCtaButtonSettingsState enabled =
                new MessageCtaButtonSettingsState(true, MessageCtaButtonAction.HIDE);
        assertNull(resolve(NoisePipelineOutcome.TERMINAL_ALLOW, matching, enabled));
        assertNull(resolve(NoisePipelineOutcome.TERMINAL_DECISION, matching, enabled));
        assertNull(resolve(NoisePipelineOutcome.FAIL_OPEN_STOP, matching, enabled));
        assertNull(resolve(null, matching, enabled));
    }

    @Test
    public void existingApplierNeutralizesCtaStateOnActionSwitchAndNoMatch() {
        TelegramDecisionApplier applier = new TelegramDecisionApplier(() -> "collapsed");
        TelegramDecisionTestFixtures.TestMessage message =
                TelegramDecisionTestFixtures.message(1, 100L, 0L, 10);
        TelegramDecisionTestFixtures.TestCell cell = TelegramDecisionTestFixtures.cell(message);
        TelegramDecisionContext context = new TelegramDecisionContext(
                1, message, message, cell,
                TelegramDecisionContext.PresentationRole.SINGLE, null);
        TelegramCollapseState collapseState = new TelegramCollapseState();
        applier.apply(new NoiseDecision(NoiseAction.HIDE), context, collapseState);
        assertTrue(cell.hidden);
        applier.apply(new NoiseDecision(NoiseAction.COLLAPSE), context, collapseState);
        assertTrue(cell.compact);
        assertFalse(cell.hidden);
        applier.apply(new NoiseDecision(NoiseAction.ALLOW), context, collapseState);
        assertFalse(cell.compact);
        assertFalse(cell.hidden);
        assertEquals(1, cell.resetCalls);
    }

    private static NoiseAction resolve(
            NoisePipelineOutcome outcome,
            MessageCtaClassificationInput input,
            MessageCtaButtonSettingsState settings
    ) {
        return TelegramMessageCtaPresentationPolicy.resolveAction(outcome, input, settings);
    }

    private static MessageCtaClassificationInput inputWith(TLRPC.ReplyMarkup replyMarkup) {
        return new MessageCtaClassificationInput(replyMarkup, false, false);
    }

    private static TLRPC.TL_replyInlineMarkup singleUrlRow() {
        TLRPC.TL_keyboardButtonRow row = new TLRPC.TL_keyboardButtonRow();
        row.buttons.add(new TLRPC.TL_keyboardButtonUrl());
        TLRPC.TL_replyInlineMarkup markup = new TLRPC.TL_replyInlineMarkup();
        markup.rows.add(row);
        return markup;
    }
}
