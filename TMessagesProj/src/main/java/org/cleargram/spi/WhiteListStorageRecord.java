package org.cleargram.spi;

import java.util.Objects;

/** Platform-neutral record exchanged through the storage SPI. */
public final class WhiteListStorageRecord {

    private final String canonicalPattern;
    private final boolean enabled;

    public WhiteListStorageRecord(String canonicalPattern, boolean enabled) {
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
