package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Applies Core decisions to Telegram UI.
 */
public final class TelegramDecisionApplier {

    interface CompactPresentationTextProvider {
        CharSequence getText();
    }

    private final CompactPresentationTextProvider compactPresentationTextProvider;

    public TelegramDecisionApplier() {
        this(() -> LocaleController.getString(R.string.CleargramMessageCollapsed));
    }

    TelegramDecisionApplier(CompactPresentationTextProvider compactPresentationTextProvider) {
        this.compactPresentationTextProvider = compactPresentationTextProvider;
    }

    /** Applies an existing decision after emitting fail-open Integration Layer diagnostics. */
    public void applyWithDiagnostics(
            NoiseDecision decision,
            TelegramDecisionContext context,
            TelegramCollapseState collapseState,
            boolean grouped,
            String source,
            boolean ctaMatched
    ) {
        TelegramDecisionDiagnostics.log(decision, context, grouped, source, ctaMatched);
        apply(decision, context, collapseState);
    }

    public void apply(NoiseDecision decision, TelegramDecisionContext context, TelegramCollapseState collapseState) {
        TelegramDecisionContext.PresentationTarget presentationTarget = context != null ? context.getPresentationTarget() : null;
        if (presentationTarget == null) {
            return;
        }
        try {
            if (!context.isCurrent() || collapseState == null) {
                failOpen(presentationTarget);
                return;
            }
            NoiseAction action = decision != null ? decision.getAction() : null;
            if (action == NoiseAction.HIDE) {
                collapseState.remove(context);
                TelegramHideSelectionLifecycle.clearCurrentMessageSelection(
                        context.getMessageCell(), context.getBoundMessage());
                presentationTarget.applyHiddenPresentation();
            } else if (action == NoiseAction.COLLAPSE) {
                if (collapseState.isExpanded(context)) {
                    presentationTarget.resetPresentation();
                } else if (context.getPresentationRole() == TelegramDecisionContext.PresentationRole.GROUP_MEMBER) {
                    presentationTarget.applyHiddenPresentation();
                } else {
                    applyCollapse(presentationTarget, context, collapseState);
                }
            } else {
                collapseState.remove(context);
                presentationTarget.resetPresentation();
            }
        } catch (Throwable ignored) {
            failOpen(presentationTarget);
        }
    }

    private void applyCollapse(TelegramDecisionContext.PresentationTarget presentationTarget, TelegramDecisionContext context, TelegramCollapseState collapseState) {
        presentationTarget.applyCompactPresentation(
                compactPresentationTextProvider.getText(),
                () -> expand(context, collapseState)
        );
    }

    private void expand(TelegramDecisionContext context, TelegramCollapseState collapseState) {
        TelegramDecisionContext.PresentationTarget presentationTarget = context != null ? context.getPresentationTarget() : null;
        if (presentationTarget == null) {
            return;
        }
        try {
            if (collapseState == null || !context.isCurrent()) {
                failOpen(presentationTarget);
                return;
            }
            collapseState.markExpanded(context);
            presentationTarget.resetPresentation();
            context.requestPresentationUnitRebind();
        } catch (Throwable ignored) {
            failOpen(presentationTarget);
        }
    }

    private void failOpen(TelegramDecisionContext.PresentationTarget presentationTarget) {
        try {
            presentationTarget.resetPresentation();
        } catch (Throwable ignored) {
            // Fail-open: Telegram should continue normal message binding.
        }
    }
}
