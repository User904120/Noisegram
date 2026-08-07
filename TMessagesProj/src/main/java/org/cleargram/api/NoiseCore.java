package org.cleargram.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.cleargram.internal.WhiteListEvaluator;
import org.cleargram.internal.WhiteListRuleManager;
import org.cleargram.internal.BlackListEvaluator;
import org.cleargram.internal.BlackListRuleManager;
import org.cleargram.internal.DuplicateVideoManager;
import org.cleargram.internal.DuplicateVideoManagerResult;
import org.cleargram.internal.DuplicateVideoManagerState;

/**
 * Public Core facade exposed to Telegram-facing code.
 *
 * <p>The instance is created and owned by {@code NoiseBootstrap}.</p>
 */
public final class NoiseCore {

    NoiseCore() {
        this(WhiteListRuleManager.createEmpty(), BlackListRuleManager.createEmpty(), DuplicateVideoManager.createInMemory());
    }

    NoiseCore(WhiteListRuleManager whiteListRuleManager) {
        this(whiteListRuleManager, BlackListRuleManager.createEmpty(), DuplicateVideoManager.createInMemory());
    }

    NoiseCore(WhiteListRuleManager whiteListRuleManager, BlackListRuleManager blackListRuleManager) {
        this(whiteListRuleManager, blackListRuleManager, DuplicateVideoManager.createInMemory());
    }

    NoiseCore(WhiteListRuleManager whiteListRuleManager, BlackListRuleManager blackListRuleManager,
            DuplicateVideoManager duplicateVideoManager) {
        this(
                whiteListRuleManager,
                whiteListRuleManager.createEvaluator(),
                blackListRuleManager,
                blackListRuleManager.createEvaluator(), duplicateVideoManager);
    }

    NoiseCore(WhiteListRuleManager whiteListRuleManager, WhiteListEvaluator evaluator) {
        this(
                whiteListRuleManager,
                evaluator,
                BlackListRuleManager.createEmpty(),
                BlackListEvaluator.createEmpty(), DuplicateVideoManager.createInMemory());
    }

    NoiseCore(
            WhiteListRuleManager whiteListRuleManager,
            WhiteListEvaluator evaluator,
            BlackListRuleManager blackListRuleManager,
            BlackListEvaluator blackListEvaluator
    ) {
        this(whiteListRuleManager, evaluator, blackListRuleManager, blackListEvaluator,
                DuplicateVideoManager.createInMemory());
    }

    NoiseCore(
            WhiteListRuleManager whiteListRuleManager,
            WhiteListEvaluator evaluator,
            BlackListRuleManager blackListRuleManager,
            BlackListEvaluator blackListEvaluator,
            DuplicateVideoManager duplicateVideoManager
    ) {
        this.whiteListRuleManager = Objects.requireNonNull(whiteListRuleManager, "whiteListRuleManager");
        this.whiteListEvaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.blackListRuleManager = Objects.requireNonNull(blackListRuleManager, "blackListRuleManager");
        this.blackListEvaluator = Objects.requireNonNull(blackListEvaluator, "blackListEvaluator");
        this.duplicateVideoManager = Objects.requireNonNull(duplicateVideoManager, "duplicateVideoManager");
    }

    private final WhiteListRuleManager whiteListRuleManager;
    private final WhiteListEvaluator whiteListEvaluator;
    private final BlackListRuleManager blackListRuleManager;
    private final BlackListEvaluator blackListEvaluator;
    private final DuplicateVideoManager duplicateVideoManager;

    public NoiseDecision evaluate(NoiseMessage message) {
        return evaluatePipeline(message).getDecision();
    }

    public NoiseEvaluation evaluatePipeline(NoiseMessage message) {
        Objects.requireNonNull(message, "message");
        if (!whiteListEvaluator.isReady()) {
            return evaluation(NoiseAction.ALLOW, NoisePipelineOutcome.FAIL_OPEN_STOP);
        }
        String normalizedText = message.getText().toLowerCase(Locale.ROOT);
        if (whiteListEvaluator.matchesNormalized(normalizedText)) {
            return evaluation(NoiseAction.ALLOW, NoisePipelineOutcome.TERMINAL_ALLOW);
        }
        if (!blackListEvaluator.isReady()) {
            return evaluation(NoiseAction.ALLOW, NoisePipelineOutcome.FAIL_OPEN_STOP);
        }
        NoiseAction blackListAction = blackListEvaluator.firstMatchNormalized(normalizedText);
        if (blackListAction != null) {
            return evaluation(blackListAction, NoisePipelineOutcome.TERMINAL_DECISION);
        }
        return evaluation(NoiseAction.ALLOW, NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO);
    }

    private NoiseEvaluation evaluation(NoiseAction action, NoisePipelineOutcome outcome) {
        return new NoiseEvaluation(new NoiseDecision(action), outcome);
    }

    public List<WhiteListRuleSnapshot> getWhiteListRules() {
        return whiteListRuleManager.getWhiteListRules();
    }

    public WhiteListRuleSnapshot addWhiteListRule(String pattern, boolean enabled) {
        return whiteListRuleManager.addWhiteListRule(pattern, enabled);
    }

    public boolean removeWhiteListRule(String pattern) {
        return whiteListRuleManager.removeWhiteListRule(pattern);
    }

    public boolean setWhiteListRuleEnabled(String pattern, boolean enabled) {
        return whiteListRuleManager.setWhiteListRuleEnabled(pattern, enabled);
    }

    public List<BlackListRuleSnapshot> getBlackListRules() {
        return blackListRuleManager.getBlackListRules();
    }

    public NoiseAction getBlackListAction() {
        return blackListRuleManager.getBlackListAction();
    }

    public void setBlackListAction(NoiseAction action) {
        blackListRuleManager.setBlackListAction(action);
    }

    public BlackListRuleSnapshot addBlackListRule(String pattern) {
        return blackListRuleManager.addBlackListRule(pattern);
    }

    public boolean removeBlackListRule(String pattern) {
        return blackListRuleManager.removeBlackListRule(pattern);
    }

    public boolean setBlackListRuleEnabled(String pattern, boolean enabled) {
        return blackListRuleManager.setBlackListRuleEnabled(pattern, enabled);
    }

    public DuplicateVideoResult classifyDuplicateVideo(DuplicateVideoBatch batch) {
        DuplicateVideoManagerResult result = duplicateVideoManager.classify(batch);
        return new DuplicateVideoResult(result.getHiddenItemIds(), result.shouldHideWholeMessage());
    }

    public DuplicateVideoStateSnapshot getDuplicateVideoState() {
        DuplicateVideoManagerState state = duplicateVideoManager.getState();
        return new DuplicateVideoStateSnapshot(state.getStatus(), state.getMatchMode());
    }

    public void setDuplicateVideoEnabled(boolean enabled) { duplicateVideoManager.setEnabled(enabled); }
    public void setDuplicateVideoMatchMode(DuplicateVideoMatchMode mode) { duplicateVideoManager.setMatchMode(mode); }
    public void clearDuplicateVideoHistory() { duplicateVideoManager.clearHistory(); }
}
