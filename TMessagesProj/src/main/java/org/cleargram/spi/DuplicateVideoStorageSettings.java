package org.cleargram.spi;

/** Raw durable Duplicate Video settings exchanged through the storage SPI. */
public final class DuplicateVideoStorageSettings {

    private final int enabled;
    private final int matchMode;

    public DuplicateVideoStorageSettings(int enabled, int matchMode) {
        this.enabled = enabled;
        this.matchMode = matchMode;
    }

    public int getEnabled() {
        return enabled;
    }

    public int getMatchMode() {
        return matchMode;
    }
}
