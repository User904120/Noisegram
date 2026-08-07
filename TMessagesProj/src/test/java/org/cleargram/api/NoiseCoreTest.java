package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.Arrays;
import org.cleargram.internal.BlackListRuleManager;
import org.cleargram.internal.DuplicateVideoManager;
import org.cleargram.internal.WhiteListEvaluator;
import org.cleargram.internal.WhiteListRuleManager;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.BlackListStorageRecord;
import org.cleargram.spi.BlackListStorageState;
import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.WhiteListStorageRecord;
import org.cleargram.spi.DuplicateVideoStorageClassification;
import org.cleargram.spi.DuplicateVideoStoragePort;
import org.cleargram.spi.DuplicateVideoStorageSettings;

public final class NoiseCoreTest {

    @Test(expected = NullPointerException.class)
    public void evaluateNullThrowsNullPointerException() {
        new NoiseCore().evaluate(null);
    }

    @Test(expected = NullPointerException.class)
    public void evaluatePipelineNullThrowsNullPointerException() {
        new NoiseCore().evaluatePipeline(null);
    }

    @Test
    public void evaluateMessageReturnsDecision() {
        NoiseDecision decision = new NoiseCore().evaluate(new NoiseMessage("text"));

        assertNotNull(decision);
    }

    @Test
    public void evaluateMessageReturnsAllowAction() {
        NoiseDecision decision = new NoiseCore().evaluate(new NoiseMessage("text"));

        assertEquals(NoiseAction.ALLOW, decision.getAction());
    }

    @Test
    public void managesWhiteListRulesThroughCoreFacade() {
        NoiseCore core = new NoiseCore(WhiteListRuleManager.createEmpty());

        assertEquals(0, core.getWhiteListRules().size());
        assertEquals("important", core.addWhiteListRule("Important", true).getCanonicalPattern());
        assertEquals(1, core.getWhiteListRules().size());
        assertTrue(core.setWhiteListRuleEnabled(" IMPORTANT ", false));
        assertFalse(core.getWhiteListRules().get(0).isEnabled());
        assertTrue(core.removeWhiteListRule("important"));
        assertTrue(core.getWhiteListRules().isEmpty());
    }

    @Test
    public void sharedManagerAndEvaluatorSeeTheSameRepository() {
        WhiteListRuleManager manager = WhiteListRuleManager.createEmpty();
        WhiteListEvaluator evaluator = manager.createEvaluator();
        NoiseCore core = new NoiseCore(manager, evaluator);

        core.addWhiteListRule("Important", true);

        assertTrue(evaluator.matches(new NoiseMessage("An IMPORTANT message")));
        assertEquals(NoiseAction.ALLOW, core.evaluate(new NoiseMessage("An IMPORTANT message")).getAction());
    }

    @Test
    public void whiteListMatchIsTerminalEvenWhenBlackListIsNotReady() {
        WhiteListRuleManager whiteList = WhiteListRuleManager.createEmpty();
        whiteList.addWhiteListRule("important", true);
        NoiseCore core = new NoiseCore(whiteList, BlackListRuleManager.createPersistent(new PendingBlackListStorage()));

        assertPipeline(core, "important message", NoiseAction.ALLOW, NoisePipelineOutcome.TERMINAL_ALLOW);
    }

    @Test
    public void blackListHideAndCollapseAreTerminalDecisions() {
        BlackListRuleManager manager = BlackListRuleManager.createEmpty();
        manager.setBlackListAction(NoiseAction.HIDE);
        manager.addBlackListRule("hide me");
        NoiseCore hideCore = new NoiseCore(WhiteListRuleManager.createEmpty(), manager);
        assertPipeline(hideCore, "hide me", NoiseAction.HIDE, NoisePipelineOutcome.TERMINAL_DECISION);

        manager.setBlackListAction(NoiseAction.COLLAPSE);
        NoiseCore collapseCore = new NoiseCore(WhiteListRuleManager.createEmpty(), manager);
        assertPipeline(collapseCore, "hide me", NoiseAction.COLLAPSE, NoisePipelineOutcome.TERMINAL_DECISION);
    }

    @Test
    public void sharedPipelineNormalizationPreservesWhiteListPriorityAndBlackListSubstringMatching() {
        WhiteListRuleManager whiteList = WhiteListRuleManager.createEmpty();
        BlackListRuleManager blackList = BlackListRuleManager.createEmpty();
        whiteList.addWhiteListRule("approved", true);
        blackList.setBlackListAction(NoiseAction.HIDE);
        blackList.addBlackListRule("\u0441\u0442\u0430\u0432\u043a\u0438");
        NoiseCore core = new NoiseCore(whiteList, blackList);

        assertPipeline(core, "APPROVED \u0441\u0442\u0430\u0432\u043a\u0438", NoiseAction.ALLOW,
                NoisePipelineOutcome.TERMINAL_ALLOW);
        assertPipeline(core, "\u041f\u041e\u0421\u0422\u0410\u0412\u041a\u0418", NoiseAction.HIDE,
                NoisePipelineOutcome.TERMINAL_DECISION);
        assertPipeline(core, "ordinary message", NoiseAction.ALLOW,
                NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO);
    }

    @Test
    public void normalNoMatchContinuesDuplicateVideoPipeline() {
        assertPipeline(new NoiseCore(), "ordinary message", NoiseAction.ALLOW,
                NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO);
    }

    @Test
    public void defaultCoreOwnsFunctionalInMemoryDuplicateVideoManager() {
        NoiseCore core = new NoiseCore();
        assertEquals(DuplicateVideoRuntimeStatus.DISABLED, core.getDuplicateVideoState().getStatus());
        assertEquals(DuplicateVideoMatchMode.VIDEO, core.getDuplicateVideoState().getMatchMode());
        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(new DuplicateVideoItem(7L, new byte[32])), false);
        assertTrue(core.classifyDuplicateVideo(batch).getHiddenItemIds().isEmpty());
        core.setDuplicateVideoEnabled(true);
        assertEquals(DuplicateVideoRuntimeStatus.READY, core.getDuplicateVideoState().getStatus());
        assertTrue(core.classifyDuplicateVideo(batch).getHiddenItemIds().isEmpty());
        DuplicateVideoResult duplicate = core.classifyDuplicateVideo(batch);
        assertEquals(Arrays.asList(7L), duplicate.getHiddenItemIds());
        assertTrue(duplicate.shouldHideWholeMessage());
        core.clearDuplicateVideoHistory();
        assertTrue(core.classifyDuplicateVideo(batch).getHiddenItemIds().isEmpty());
    }

    @Test
    public void persistentDuplicateVideoCoreIsFailOpenUntilReadyAndDelegatesManagementAfterLoad() {
        PendingDuplicateVideoStorage storage = new PendingDuplicateVideoStorage();
        NoiseCore core = persistentDuplicateVideoCore(storage);
        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(new DuplicateVideoItem(9L, new byte[32])), false);

        assertEquals(1, storage.loadCalls);
        assertTrue(core.classifyDuplicateVideo(batch).getHiddenItemIds().isEmpty());
        expectIllegalState(core::getDuplicateVideoState);
        expectIllegalState(() -> core.setDuplicateVideoEnabled(true));

        storage.loaded(new DuplicateVideoStorageSettings(0, 1));
        assertEquals(DuplicateVideoRuntimeStatus.DISABLED, core.getDuplicateVideoState().getStatus());
        core.setDuplicateVideoEnabled(true);
        assertEquals(DuplicateVideoRuntimeStatus.READY, core.getDuplicateVideoState().getStatus());
        core.setDuplicateVideoMatchMode(DuplicateVideoMatchMode.VIDEO_AND_TEXT);
        core.clearDuplicateVideoHistory();
        assertEquals(1, storage.setEnabledCalls);
        assertEquals(1, storage.setModeCalls);
        assertEquals(1, storage.clearCalls);
    }

    @Test
    public void failedPersistentDuplicateVideoCoreRemainsFailOpenAndRejectsManagement() {
        PendingDuplicateVideoStorage storage = new PendingDuplicateVideoStorage();
        NoiseCore core = persistentDuplicateVideoCore(storage);
        DuplicateVideoBatch batch = new DuplicateVideoBatch(DuplicateVideoMatchMode.VIDEO, 1,
                Arrays.asList(new DuplicateVideoItem(10L, new byte[32])), false);

        storage.failed(new IllegalStateException("load failure"));

        assertEquals(DuplicateVideoRuntimeStatus.FAILED, core.getDuplicateVideoState().getStatus());
        assertTrue(core.classifyDuplicateVideo(batch).getHiddenItemIds().isEmpty());
        expectIllegalState(core::clearDuplicateVideoHistory);
    }

    @Test
    public void whiteListNotReadyStopsPipelineFailOpen() {
        NoiseCore core = new NoiseCore(
                WhiteListRuleManager.createPersistent(new PendingWhiteListStorage()),
                BlackListRuleManager.createEmpty());

        assertPipeline(core, "ordinary message", NoiseAction.ALLOW, NoisePipelineOutcome.FAIL_OPEN_STOP);
    }

    @Test
    public void blackListNotReadyAfterWhiteListNoMatchStopsPipelineFailOpen() {
        NoiseCore core = new NoiseCore(
                WhiteListRuleManager.createEmpty(),
                BlackListRuleManager.createPersistent(new PendingBlackListStorage()));

        assertPipeline(core, "ordinary message", NoiseAction.ALLOW, NoisePipelineOutcome.FAIL_OPEN_STOP);
    }

    private static void assertPipeline(
            NoiseCore core,
            String text,
            NoiseAction action,
            NoisePipelineOutcome outcome
    ) {
        NoiseMessage message = new NoiseMessage(text);
        NoiseEvaluation evaluation = core.evaluatePipeline(message);
        assertEquals(action, evaluation.getDecision().getAction());
        assertEquals(outcome, evaluation.getOutcome());
        assertEquals(core.evaluate(message).getAction(), evaluation.getDecision().getAction());
    }

    private static NoiseCore persistentDuplicateVideoCore(PendingDuplicateVideoStorage storage) {
        WhiteListRuleManager whiteListManager = WhiteListRuleManager.createEmpty();
        BlackListRuleManager blackListManager = BlackListRuleManager.createEmpty();
        return new NoiseCore(
                whiteListManager, whiteListManager.createEvaluator(),
                blackListManager, blackListManager.createEvaluator(),
                DuplicateVideoManager.createPersistent(storage));
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected persistent readiness contract.
        }
    }

    private static final class PendingWhiteListStorage implements WhiteListStoragePort {

        @Override public void loadRules(LoadCallback callback) { }
        @Override public void insertRule(WhiteListStorageRecord record) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteRule(String canonicalPattern) { throw new UnsupportedOperationException(); }
        @Override public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) { throw new UnsupportedOperationException(); }
    }

    private static final class PendingBlackListStorage implements BlackListStoragePort {

        @Override public void loadState(LoadCallback callback) { }
        @Override public void insertRule(BlackListStorageRecord record) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteRule(String canonicalPattern) { throw new UnsupportedOperationException(); }
        @Override public boolean updateRuleEnabled(String canonicalPattern, boolean enabled) { throw new UnsupportedOperationException(); }
        @Override public void updateAction(NoiseAction action) { throw new UnsupportedOperationException(); }
    }

    private static final class PendingDuplicateVideoStorage implements DuplicateVideoStoragePort {
        private LoadCallback callback;
        private int loadCalls;
        private int setEnabledCalls;
        private int setModeCalls;
        private int clearCalls;

        @Override public void load(LoadCallback callback) { this.callback = callback; loadCalls++; }
        private void loaded(DuplicateVideoStorageSettings settings) { callback.onLoaded(settings); }
        private void failed(Throwable error) { callback.onFailed(error); }
        @Override public void setEnabled(boolean enabled) { setEnabledCalls++; }
        @Override public void setMatchMode(int mode) { setModeCalls++; }
        @Override public java.util.List<DuplicateVideoStorageClassification> classifyOrdered(
                int mode, int keyVersion, java.util.List<byte[]> matchKeys) {
            return java.util.Collections.nCopies(
                    matchKeys.size(), DuplicateVideoStorageClassification.FIRST_SEEN);
        }
        @Override public void clearHistory() { clearCalls++; }
    }
}
