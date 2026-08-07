package org.cleargram.api;

import java.util.Objects;

/**
 * Immutable public result of a Core decision.
 */
public final class NoiseDecision {

    private final NoiseAction action;

    public NoiseDecision(NoiseAction action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    public NoiseAction getAction() {
        return action;
    }
}
