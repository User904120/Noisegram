package org.cleargram.api;

import java.util.Objects;

import org.cleargram.internal.WhiteListRuleManager;
import org.cleargram.internal.BlackListRuleManager;
import org.cleargram.internal.DuplicateVideoManager;
import org.cleargram.spi.BlackListStoragePort;
import org.cleargram.spi.WhiteListStoragePort;
import org.cleargram.spi.DuplicateVideoStoragePort;

/**
 * Public entry point for Core initialization.
 */
public final class NoiseBootstrap {

    private static boolean initialized;
    private static NoiseCore core;

    private NoiseBootstrap() {
    }

    public static synchronized void initialize(BootstrapConfiguration bootstrapConfiguration) {
        if (initialized) {
            return;
        }
        Objects.requireNonNull(bootstrapConfiguration, "bootstrapConfiguration");
        WhiteListStoragePort storagePort = bootstrapConfiguration.getWhiteListStoragePort();
        BlackListStoragePort blackListStoragePort = bootstrapConfiguration.getBlackListStoragePort();
        DuplicateVideoStoragePort duplicateVideoStoragePort = bootstrapConfiguration.getDuplicateVideoStoragePort();
        boolean hasAnyStorage = storagePort != null || blackListStoragePort != null
                || duplicateVideoStoragePort != null;
        boolean hasAllStorage = storagePort != null && blackListStoragePort != null
                && duplicateVideoStoragePort != null;
        if (hasAnyStorage && !hasAllStorage) {
            throw new IllegalArgumentException("Persistent composition requires all storage ports");
        }
        WhiteListRuleManager ruleManager = storagePort == null
                ? WhiteListRuleManager.createEmpty()
                : WhiteListRuleManager.createPersistent(storagePort);
        BlackListRuleManager blackListRuleManager = blackListStoragePort == null
                ? BlackListRuleManager.createEmpty()
                : BlackListRuleManager.createPersistent(blackListStoragePort);
        DuplicateVideoManager duplicateVideoManager = duplicateVideoStoragePort == null
                ? DuplicateVideoManager.createInMemory()
                : DuplicateVideoManager.createPersistent(duplicateVideoStoragePort);
        core = new NoiseCore(
                ruleManager,
                ruleManager.createEvaluator(),
                blackListRuleManager,
                blackListRuleManager.createEvaluator(), duplicateVideoManager);
        initialized = true;
    }

    public static synchronized NoiseCore getCore() {
        if (!initialized) {
            throw new IllegalStateException("NoiseBootstrap is not initialized");
        }
        return core;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }
}
