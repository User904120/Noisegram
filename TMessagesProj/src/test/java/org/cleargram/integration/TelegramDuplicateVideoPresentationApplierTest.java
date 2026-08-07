package org.cleargram.integration;

import static org.junit.Assert.assertEquals;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.telegram.messenger.MessageObject;

public final class TelegramDuplicateVideoPresentationApplierTest {

    @Test
    public void disabledProviderSuppressesPendingPresentationResult() {
        ManualUiDispatcher ui = new ManualUiDispatcher();
        MutableEnabledProvider enabled = new MutableEnabledProvider(true);
        TelegramDuplicateVideoPresentationApplier applier = new TelegramDuplicateVideoPresentationApplier(
                new TelegramDecisionApplier(() -> "collapsed"), new TelegramCollapseState(), ui, enabled);
        RecordingTarget target = new RecordingTarget();
        LogicalPresentationId logicalId = LogicalPresentationId.singleMessage(0, 1L, 0L, 7);
        applier.registerPresentationItem(logicalId, 7L, context(target));
        ui.runAll();

        applier.onDuplicateVideoPresentation(result(logicalId));
        enabled.value = false;
        ui.runAll();

        assertEquals(0, target.hiddenCalls);
        assertEquals(0, target.resetCalls);
    }

    @Test
    public void enabledProviderAllowsExistingPresentationFlowToRun() {
        ManualUiDispatcher ui = new ManualUiDispatcher();
        MutableEnabledProvider enabled = new MutableEnabledProvider(true);
        TelegramDuplicateVideoPresentationApplier applier = new TelegramDuplicateVideoPresentationApplier(
                new TelegramDecisionApplier(() -> "collapsed"), new TelegramCollapseState(), ui, enabled);
        RecordingTarget target = new RecordingTarget();
        LogicalPresentationId logicalId = LogicalPresentationId.singleMessage(0, 1L, 0L, 8);
        applier.registerPresentationItem(logicalId, 8L, context(target));
        ui.runAll();

        applier.onDuplicateVideoPresentation(result(logicalId, 8L));
        ui.runAll();

        assertEquals(1, target.resetCalls);
    }

    private static DuplicateVideoCoordinatorResult result(LogicalPresentationId logicalId) {
        return result(logicalId, 7L);
    }

    private static DuplicateVideoCoordinatorResult result(LogicalPresentationId logicalId, long itemId) {
        return new DuplicateVideoCoordinatorResult(logicalId, 1L, Collections.singletonList(itemId),
                Collections.singletonList(itemId), false);
    }

    private static TelegramDecisionContext context(RecordingTarget target) {
        return new TelegramDecisionContext(0, null, null, target,
                TelegramDecisionContext.PresentationRole.SINGLE, null);
    }

    private static final class MutableEnabledProvider
            implements TelegramDuplicateVideoPresentationApplier.EnabledStateProvider {
        boolean value;

        MutableEnabledProvider(boolean value) { this.value = value; }

        @Override public boolean isDuplicateVideoEnabled() { return value; }
    }

    private static final class ManualUiDispatcher
            implements TelegramDuplicateVideoPresentationApplier.UiDispatcher {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override public void post(Runnable runnable) { work.addLast(runnable); }

        void runAll() {
            while (!work.isEmpty()) work.removeFirst().run();
        }
    }

    private static final class RecordingTarget implements TelegramDecisionContext.PresentationTarget {
        int hiddenCalls;
        int resetCalls;

        @Override public MessageObject getMessageObject() { return null; }
        @Override public void applyCompactPresentation(CharSequence text, Runnable expansionCallback) { }
        @Override public void applyHiddenPresentation() { hiddenCalls++; }
        @Override public void resetPresentation() { resetCalls++; }
    }
}
