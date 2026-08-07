package org.cleargram.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;
import org.telegram.messenger.AndroidUtilities;

/**
 * Chat-scoped UI consumer for immutable Duplicate Video presentation results.
 *
 * <p>It owns no classification, storage, resolver, or hashing dependency.
 * Presentation IDs are the only key used to locate registered current targets.</p>
 */
final class TelegramDuplicateVideoPresentationApplier
        implements DuplicateVideoPresentationConsumer {

    interface UiDispatcher {
        void post(Runnable runnable);
    }

    interface EnabledStateProvider {
        boolean isDuplicateVideoEnabled();
    }

    private static final NoiseDecision HIDE_DECISION = new NoiseDecision(NoiseAction.HIDE);
    private static final NoiseDecision ALLOW_DECISION = new NoiseDecision(NoiseAction.ALLOW);

    private final TelegramDecisionApplier decisionApplier;
    private final TelegramCollapseState collapseState;
    private final UiDispatcher uiDispatcher;
    private final EnabledStateProvider enabledStateProvider;
    private final Map<LogicalPresentationId, Map<Long, TelegramDecisionContext>> targets = new HashMap<>();
    private final Map<LogicalPresentationId, Set<Long>> duplicateHiddenItemIds = new HashMap<>();
    private final Map<LogicalPresentationId, Long> lastAppliedGenerations = new HashMap<>();
    private volatile boolean closed;

    TelegramDuplicateVideoPresentationApplier(TelegramCollapseState collapseState) {
        this(new TelegramDecisionApplier(), collapseState, AndroidUtilities::runOnUIThread, () -> true);
    }

    TelegramDuplicateVideoPresentationApplier(
            TelegramCollapseState collapseState,
            EnabledStateProvider enabledStateProvider
    ) {
        this(new TelegramDecisionApplier(), collapseState, AndroidUtilities::runOnUIThread,
                enabledStateProvider);
    }

    TelegramDuplicateVideoPresentationApplier(
            TelegramDecisionApplier decisionApplier,
            TelegramCollapseState collapseState,
            UiDispatcher uiDispatcher
    ) {
        this(decisionApplier, collapseState, uiDispatcher, () -> true);
    }

    TelegramDuplicateVideoPresentationApplier(
            TelegramDecisionApplier decisionApplier,
            TelegramCollapseState collapseState,
            UiDispatcher uiDispatcher,
            EnabledStateProvider enabledStateProvider
    ) {
        this.decisionApplier = Objects.requireNonNull(decisionApplier, "decisionApplier");
        this.collapseState = Objects.requireNonNull(collapseState, "collapseState");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
        this.enabledStateProvider = Objects.requireNonNull(enabledStateProvider, "enabledStateProvider");
    }

    void registerPresentationItem(
            LogicalPresentationId logicalPresentationId,
            long presentationItemId,
            TelegramDecisionContext context
    ) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        Objects.requireNonNull(context, "context");
        uiDispatcher.post(() -> registerOnUiThread(logicalPresentationId, presentationItemId, context));
    }

    void unregisterPresentationItem(
            LogicalPresentationId logicalPresentationId,
            long presentationItemId
    ) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        uiDispatcher.post(() -> {
            Map<Long, TelegramDecisionContext> logicalTargets = targets.get(logicalPresentationId);
            if (logicalTargets != null) {
                logicalTargets.remove(presentationItemId);
            }
        });
    }

    void clearLogicalPresentation(LogicalPresentationId logicalPresentationId) {
        clearLogicalPresentation(logicalPresentationId, 0L);
    }

    void clearLogicalPresentation(
            LogicalPresentationId logicalPresentationId,
            long minimumAcceptedGeneration
    ) {
        Objects.requireNonNull(logicalPresentationId, "logicalPresentationId");
        uiDispatcher.post(() -> {
            Map<Long, TelegramDecisionContext> logicalTargets = targets.remove(logicalPresentationId);
            Set<Long> hiddenItemIds = duplicateHiddenItemIds.remove(logicalPresentationId);
            if (logicalTargets != null && hiddenItemIds != null) {
                for (Long presentationItemId : hiddenItemIds) {
                    TelegramDecisionContext context = logicalTargets.get(presentationItemId);
                    if (context != null) {
                        apply(context, ALLOW_DECISION);
                    }
                }
            }
            if (minimumAcceptedGeneration > 0L) {
                Long previous = lastAppliedGenerations.get(logicalPresentationId);
                if (previous == null || previous < minimumAcceptedGeneration) {
                    lastAppliedGenerations.put(logicalPresentationId, minimumAcceptedGeneration);
                }
            } else {
                lastAppliedGenerations.remove(logicalPresentationId);
            }
        });
    }

    void close() {
        closed = true;
        uiDispatcher.post(() -> {
            targets.clear();
            duplicateHiddenItemIds.clear();
            lastAppliedGenerations.clear();
        });
    }

    @Override
    public void onDuplicateVideoPresentation(DuplicateVideoCoordinatorResult result) {
        if (result == null) {
            return;
        }
        uiDispatcher.post(() -> applyOnUiThread(result));
    }

    private void registerOnUiThread(
            LogicalPresentationId logicalPresentationId,
            long presentationItemId,
            TelegramDecisionContext context
    ) {
        if (closed) {
            return;
        }
        Map<Long, TelegramDecisionContext> logicalTargets = targets.get(logicalPresentationId);
        if (logicalTargets == null) {
            logicalTargets = new HashMap<>();
            targets.put(logicalPresentationId, logicalTargets);
        }
        logicalTargets.put(presentationItemId, context);
        Set<Long> hiddenItemIds = duplicateHiddenItemIds.get(logicalPresentationId);
        if (enabledStateProvider.isDuplicateVideoEnabled()
                && hiddenItemIds != null && hiddenItemIds.contains(presentationItemId)) {
            apply(context, HIDE_DECISION);
        }
    }

    private void applyOnUiThread(DuplicateVideoCoordinatorResult result) {
        if (closed || !enabledStateProvider.isDuplicateVideoEnabled()) {
            return;
        }
        LogicalPresentationId logicalPresentationId = result.getLogicalPresentationId();
        Long lastAppliedGeneration = lastAppliedGenerations.get(logicalPresentationId);
        if (lastAppliedGeneration != null && result.getGeneration() <= lastAppliedGeneration) {
            return;
        }
        lastAppliedGenerations.put(logicalPresentationId, result.getGeneration());

        Set<Long> hiddenIds = new HashSet<>(result.getHiddenPresentationItemIds());
        Set<Long> logicalHiddenItemIds = duplicateHiddenItemIds.get(logicalPresentationId);
        if (logicalHiddenItemIds == null) {
            logicalHiddenItemIds = new HashSet<>();
            duplicateHiddenItemIds.put(logicalPresentationId, logicalHiddenItemIds);
        }
        Map<Long, TelegramDecisionContext> logicalTargets = targets.get(logicalPresentationId);
        List<PresentationUpdate> updates = new ArrayList<>(result.getPresentationItemIds().size());
        for (Long presentationItemId : result.getPresentationItemIds()) {
            boolean wasDuplicateHidden = logicalHiddenItemIds.remove(presentationItemId);
            boolean shouldHide = result.shouldHideWholeMessage()
                    || hiddenIds.contains(presentationItemId);
            if (shouldHide) {
                logicalHiddenItemIds.add(presentationItemId);
            }
            if (shouldHide || wasDuplicateHidden) {
                TelegramDecisionContext context = logicalTargets != null
                        ? logicalTargets.get(presentationItemId) : null;
                if (context != null) {
                    updates.add(new PresentationUpdate(context,
                            shouldHide ? HIDE_DECISION : ALLOW_DECISION));
                }
            }
        }
        for (PresentationUpdate update : updates) {
            apply(update.context, update.decision);
        }
    }

    private void apply(TelegramDecisionContext context, NoiseDecision decision) {
        try {
            decisionApplier.applyWithDiagnostics(decision, context, collapseState,
                    context != null && context.getPresentationRole() != TelegramDecisionContext.PresentationRole.SINGLE,
                    "DUPLICATE_VIDEO", false);
        } catch (Throwable ignored) {
            try {
                decisionApplier.apply(ALLOW_DECISION, context, collapseState);
            } catch (Throwable ignoredAgain) {
                // Fail-open: presentation binding remains under Telegram ownership.
            }
        }
    }

    private static final class PresentationUpdate {

        private final TelegramDecisionContext context;
        private final NoiseDecision decision;

        private PresentationUpdate(TelegramDecisionContext context, NoiseDecision decision) {
            this.context = context;
            this.decision = decision;
        }
    }
}
