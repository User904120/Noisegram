package org.cleargram.internal;

import java.util.Objects;

/**
 * Immutable internal White List rule value object.
 */
final class WhiteListRule {

    private final String canonicalPattern;
    private final boolean enabled;

    WhiteListRule(String canonicalPattern, boolean enabled) {
        this.canonicalPattern = Objects.requireNonNull(canonicalPattern, "canonicalPattern");
        if (canonicalPattern.isEmpty()) {
            throw new IllegalArgumentException("canonicalPattern must not be empty");
        }
        this.enabled = enabled;
    }

    String getCanonicalPattern() {
        return canonicalPattern;
    }

    boolean isEnabled() {
        return enabled;
    }
}
