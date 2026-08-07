package org.cleargram.internal;

import java.util.Objects;

import org.cleargram.api.NoiseAction;

/** Immutable internal Black List rule value object. */
final class BlackListRule {

    private final String canonicalPattern;
    private final NoiseAction action;
    private final boolean enabled;

    BlackListRule(String canonicalPattern, NoiseAction action, boolean enabled) {
        this.canonicalPattern = Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        if (canonicalPattern.isEmpty()) {
            throw new IllegalArgumentException("canonicalPattern must not be empty");
        }
        this.action = requireAction(action);
        this.enabled = enabled;
    }

    String getCanonicalPattern() {
        return canonicalPattern;
    }

    NoiseAction getAction() {
        return action;
    }

    boolean isEnabled() {
        return enabled;
    }

    private static NoiseAction requireAction(NoiseAction action) {
        Objects.requireNonNull(action, "action");
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
        return action;
    }
}
