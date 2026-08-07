package org.cleargram.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class NoiseEvaluationTest {

    @Test
    public void acceptsEveryApprovedDecisionOutcomePair() {
        assertPair(NoiseAction.ALLOW, NoisePipelineOutcome.TERMINAL_ALLOW);
        assertPair(NoiseAction.HIDE, NoisePipelineOutcome.TERMINAL_DECISION);
        assertPair(NoiseAction.COLLAPSE, NoisePipelineOutcome.TERMINAL_DECISION);
        assertPair(NoiseAction.ALLOW, NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO);
        assertPair(NoiseAction.ALLOW, NoisePipelineOutcome.FAIL_OPEN_STOP);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullDecision() {
        new NoiseEvaluation(null, NoisePipelineOutcome.TERMINAL_ALLOW);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullOutcome() {
        new NoiseEvaluation(new NoiseDecision(NoiseAction.ALLOW), null);
    }

    @Test
    public void rejectsEveryOtherDecisionOutcomePair() {
        for (NoiseAction action : NoiseAction.values()) {
            for (NoisePipelineOutcome outcome : NoisePipelineOutcome.values()) {
                if (isApproved(action, outcome)) {
                    continue;
                }
                try {
                    new NoiseEvaluation(new NoiseDecision(action), outcome);
                    fail("Expected invalid pair: " + action + "/" + outcome);
                } catch (IllegalArgumentException expected) {
                    // Expected.
                }
            }
        }
    }

    private static void assertPair(NoiseAction action, NoisePipelineOutcome outcome) {
        NoiseEvaluation evaluation = new NoiseEvaluation(new NoiseDecision(action), outcome);
        assertEquals(action, evaluation.getDecision().getAction());
        assertEquals(outcome, evaluation.getOutcome());
    }

    private static boolean isApproved(NoiseAction action, NoisePipelineOutcome outcome) {
        return (action == NoiseAction.ALLOW
                && (outcome == NoisePipelineOutcome.TERMINAL_ALLOW
                || outcome == NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO
                || outcome == NoisePipelineOutcome.FAIL_OPEN_STOP))
                || ((action == NoiseAction.HIDE || action == NoiseAction.COLLAPSE)
                && outcome == NoisePipelineOutcome.TERMINAL_DECISION);
    }
}
