package org.cleargram.spi;

import java.util.Objects;

import org.cleargram.api.NoiseAction;

/** Platform-neutral record exchanged through the Black List storage SPI. */
public final class BlackListStorageRecord {

    private final String canonicalPattern;
    private final NoiseAction action;
    private final boolean enabled;

    public BlackListStorageRecord(String canonicalPattern, NoiseAction action, boolean enabled) {
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

    private static NoiseAction requireBlackListAction(NoiseAction action) {
        Objects.requireNonNull(action, "action");
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            throw new IllegalArgumentException("Black List action must be HIDE or COLLAPSE");
        }
        return action;
    }
}
