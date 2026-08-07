package org.cleargram.api;

import java.util.Objects;

/** Immutable public snapshot of a Black List rule. */
public final class BlackListRuleSnapshot {

    private final String canonicalPattern;
    private final NoiseAction action;
    private final boolean enabled;

    public BlackListRuleSnapshot(String canonicalPattern, NoiseAction action, boolean enabled) {
        this.canonicalPattern = Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        if (canonicalPattern.isEmpty()) {
            throw new IllegalArgumentException("canonicalPattern must not be empty");
        }
        this.action = requireBlackListAction(action);
        this.enabled = enabled;
    }

    public String getCanonicalPattern() {
        return canonicalPattern;
    }

    public NoiseAction getAction() {
        return action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    static NoiseAction requireBlackListAction(NoiseAction action) {
        Objects.requireNonNull(action, "action");
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
        return action;
    }
}
