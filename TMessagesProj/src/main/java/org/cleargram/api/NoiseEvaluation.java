package org.cleargram.api;

import java.util.Objects;

/** Immutable result of synchronous Core evaluation and pipeline continuation. */
public final class NoiseEvaluation {

    private final NoiseDecision decision;
    private final NoisePipelineOutcome outcome;

    NoiseEvaluation(NoiseDecision decision, NoisePipelineOutcome outcome) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        validatePair(decision.getAction(), outcome);
    }

    public NoiseDecision getDecision() {
        return decision;
    }

    public NoisePipelineOutcome getOutcome() {
        return outcome;
    }

    private static void validatePair(NoiseAction action, NoisePipelineOutcome outcome) {
        boolean valid = (action == NoiseAction.ALLOW
                && (outcome == NoisePipelineOutcome.TERMINAL_ALLOW
                || outcome == NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO
                || outcome == NoisePipelineOutcome.FAIL_OPEN_STOP))
                || ((action == NoiseAction.HIDE || action == NoiseAction.COLLAPSE)
                && outcome == NoisePipelineOutcome.TERMINAL_DECISION);
        if (!valid) {
            throw new IllegalArgumentException("invalid decision and pipeline outcome combination");
        }
    }
}
