package org.cleargram.api;

/** Synchronous Core pipeline outcome for future Duplicate Video continuation. */
public enum NoisePipelineOutcome {
    TERMINAL_ALLOW,
    TERMINAL_DECISION,
    CONTINUE_DUPLICATE_VIDEO,
    FAIL_OPEN_STOP
}
