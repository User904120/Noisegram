package org.cleargram.api;

import java.util.Objects;

/**
 * Immutable public snapshot of a White List rule.
 */
public final class WhiteListRuleSnapshot {

    private final String canonicalPattern;
    private final boolean enabled;

    public WhiteListRuleSnapshot(String canonicalPattern, boolean enabled) {
        this.canonicalPattern = Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        if (canonicalPattern.isEmpty()) {
            throw new IllegalArgumentException("canonicalPattern must not be empty");
        }
        this.enabled = enabled;
    }

    public String getCanonicalPattern() {
        return canonicalPattern;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
