package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;

/** Production bind boundary for transient footer-only text suppression. */
public final class TelegramFooterOnlyTextBindIntegration {

    public static final class Request {
        private static final Request NONE = new Request(false);
        private final boolean requested;
        private Request(boolean requested) { this.requested = requested; }
        public boolean isRequested() { return requested; }
    }

    private TelegramFooterOnlyTextBindIntegration() { }

    public static Request prepare(NoisePipelineOutcome outcome, NoiseAction ctaAction, MessageObject message,
                                  MessageObject.GroupedMessages grouped, TLRPC.Chat chat, String linkPrefix) {
        try {
            return TelegramFooterOnlyTextSuppressionPolicy.isRequested(outcome, ctaAction, message, grouped, chat, linkPrefix)
                    ? new Request(true) : Request.NONE;
        } catch (RuntimeException ignored) { return Request.NONE; }
    }

    static Request prepare(NoisePipelineOutcome outcome, NoiseAction ctaAction,
                           TelegramFooterOnlyTextSuppressionPolicy.Candidate candidate, boolean grouped,
                           TLRPC.Chat chat, String linkPrefix,
                           TelegramFooterOnlyTextSuppressionPolicy.FooterClassifier classifier) {
        try {
            return TelegramFooterOnlyTextSuppressionPolicy.isRequested(outcome, ctaAction, candidate, grouped, chat, linkPrefix, classifier)
                    ? new Request(true) : Request.NONE;
        } catch (RuntimeException ignored) { return Request.NONE; }
    }

    public static void apply(Request request, ChatMessageCell cell, TelegramDecisionContext context) {
        if (request == null || !request.requested || cell == null || context == null) return;
        try {
            if (shouldApply(request, context.isCurrent(), context.getMessageCell() == cell)) {
                TelegramHideSelectionLifecycle.clearCurrentMessageSelection(cell, context.getBoundMessage());
                cell.applyCleargramHiddenPresentation();
            }
        } catch (RuntimeException ignored) {
            try { cell.resetCleargramVisualEffect(); } catch (RuntimeException ignoredReset) { }
        }
    }

    static boolean shouldApply(Request request, boolean current, boolean sameCell) {
        return request != null && request.requested && current && sameCell;
    }
}
