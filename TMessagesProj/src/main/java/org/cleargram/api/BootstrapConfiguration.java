package org.cleargram.api;

import java.util.Objects;

import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.DuplicateVideoStoragePort;

/**
 * Configuration used during Core bootstrap.
 *
 * <p>The no-argument form selects the fully in-memory graph. The immutable
 * {@link #withRuleStorage(WhiteListStoragePort, BlackListStoragePort,
 * DuplicateVideoStoragePort)} form selects the fully persistent graph.</p>
 */
public final class BootstrapConfiguration {

    private final WhiteListStoragePort whiteListStoragePort;
    private final BlackListStoragePort blackListStoragePort;
    private final DuplicateVideoStoragePort duplicateVideoStoragePort;

    public BootstrapConfiguration() {
        whiteListStoragePort = null;
        blackListStoragePort = null;
        duplicateVideoStoragePort = null;
    }

    private BootstrapConfiguration(
            WhiteListStoragePort whiteListStoragePort,
            BlackListStoragePort blackListStoragePort,
            DuplicateVideoStoragePort duplicateVideoStoragePort
    ) {
        this.whiteListStoragePort = whiteListStoragePort;
        this.blackListStoragePort = blackListStoragePort;
        this.duplicateVideoStoragePort = duplicateVideoStoragePort;
    }

    public static BootstrapConfiguration withRuleStorage(
            WhiteListStoragePort whiteListStoragePort,
            BlackListStoragePort blackListStoragePort,
            DuplicateVideoStoragePort duplicateVideoStoragePort
    ) {
        return new BootstrapConfiguration(
                Objects.requireNonNull(whiteListStoragePort, "whiteListStoragePort"),
                Objects.requireNonNull(blackListStoragePort, "blackListStoragePort"),
                Objects.requireNonNull(duplicateVideoStoragePort, "duplicateVideoStoragePort"));
    }

    WhiteListStoragePort getWhiteListStoragePort() {
        return whiteListStoragePort;
    }

    BlackListStoragePort getBlackListStoragePort() {
        return blackListStoragePort;
    }

    DuplicateVideoStoragePort getDuplicateVideoStoragePort() { return duplicateVideoStoragePort; }
}
