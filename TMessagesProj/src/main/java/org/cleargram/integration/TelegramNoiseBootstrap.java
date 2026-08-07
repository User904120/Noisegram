package org.cleargram.integration;

import java.io.File;

import org.cleargram.api.BootstrapConfiguration;
import org.cleargram.api.NoiseBootstrap;
import org.cleargram.storage.telegram.TelegramWhiteListStorageAdapter;
import org.cleargram.storage.telegram.TelegramBlackListStorageAdapter;
import org.cleargram.storage.telegram.TelegramHideChannelEndAdvertisementStorage;
import org.cleargram.storage.telegram.TelegramHideChannelPinnedMessageHeaderStorage;
import org.cleargram.storage.telegram.TelegramHideFullscreenVideoAdvertisementStorage;
import org.cleargram.storage.telegram.TelegramHideReactionsStorage;
import org.cleargram.storage.telegram.TelegramDuplicateVideoStorageAdapter;
import org.cleargram.storage.telegram.TelegramMessageCtaButtonSettingsStorage;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/** Platform composition boundary for the process-lifetime White List storage graph. */
public final class TelegramNoiseBootstrap {

    private static final String DATABASE_FILE_NAME = "noisegram.db";
    private static final String STORAGE_QUEUE_NAME = "cleargramStorageQueue";
    private static final String DUPLICATE_VIDEO_WORKER_QUEUE_NAME = "cleargramDuplicateVideoWorkerQueue";

    private static boolean initialized;
    private static DispatchQueue storageQueue;
    private static DispatchQueue duplicateVideoWorkerQueue;
    private static TelegramWhiteListStorageAdapter storageAdapter;
    private static TelegramBlackListStorageAdapter blackListStorageAdapter;
    private static TelegramWhiteListManagementGateway whiteListManagementGateway;
    private static TelegramBlackListManagementGateway blackListManagementGateway;
    private static volatile TelegramHideReactionsGateway hideReactionsGateway;
    private static volatile TelegramHideChannelEndAdvertisementSettingsGateway
            hideChannelEndAdvertisementSettingsGateway;
    private static volatile TelegramHideFullscreenVideoAdvertisementSettingsGateway
            hideFullscreenVideoAdvertisementSettingsGateway;
    private static volatile TelegramHideChannelPinnedMessageHeaderSettingsGateway
            hideChannelPinnedMessageHeaderSettingsGateway;
    private static volatile TelegramDuplicateVideoSettingsGateway duplicateVideoSettingsGateway;
    private static volatile TelegramMessageCtaButtonSettingsGateway messageCtaButtonSettingsGateway;

    private TelegramNoiseBootstrap() {
    }

    public static synchronized void initialize(File applicationFilesDirectory) {
        if (initialized) {
            return;
        }

        try {
            validateApplicationFilesDirectory(applicationFilesDirectory);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return;
        }

        synchronized (NoiseBootstrap.class) {
            if (NoiseBootstrap.isInitialized()) {
                FileLog.e(new IllegalStateException(
                        "NoiseBootstrap was initialized before Telegram persistent composition"));
                return;
            }

            DispatchQueue localStorageQueue = null;
            DispatchQueue localDuplicateVideoWorkerQueue = null;
            TelegramWhiteListStorageAdapter localStorageAdapter = null;
            TelegramBlackListStorageAdapter localBlackListStorageAdapter = null;
            TelegramDuplicateVideoStorageAdapter localDuplicateVideoStorageAdapter = null;
            TelegramHideReactionsStorage localHideReactionsStorage;
            TelegramHideChannelEndAdvertisementStorage localHideChannelEndAdvertisementStorage;
            TelegramHideFullscreenVideoAdvertisementStorage
                    localHideFullscreenVideoAdvertisementStorage;
            TelegramHideChannelPinnedMessageHeaderStorage
                    localHideChannelPinnedMessageHeaderStorage;
            TelegramMessageCtaButtonSettingsStorage localMessageCtaButtonSettingsStorage;
            TelegramWhiteListManagementGateway localWhiteListManagementGateway;
            TelegramBlackListManagementGateway localBlackListManagementGateway;
            TelegramHideReactionsGateway localHideReactionsGateway;
            TelegramHideChannelEndAdvertisementSettingsGateway
                    localHideChannelEndAdvertisementSettingsGateway;
            TelegramHideFullscreenVideoAdvertisementSettingsGateway
                    localHideFullscreenVideoAdvertisementSettingsGateway;
            TelegramHideChannelPinnedMessageHeaderSettingsGateway
                    localHideChannelPinnedMessageHeaderSettingsGateway;
            TelegramDuplicateVideoSettingsGateway localDuplicateVideoSettingsGateway;
            TelegramMessageCtaButtonSettingsGateway localMessageCtaButtonSettingsGateway;
            boolean corePublished = false;
            try {
                File databaseFile = new File(applicationFilesDirectory, DATABASE_FILE_NAME);
                localStorageQueue = new DispatchQueue(STORAGE_QUEUE_NAME);
                localDuplicateVideoWorkerQueue = new DispatchQueue(DUPLICATE_VIDEO_WORKER_QUEUE_NAME);
                localStorageAdapter = new TelegramWhiteListStorageAdapter(databaseFile, localStorageQueue);
                localBlackListStorageAdapter = new TelegramBlackListStorageAdapter(localStorageAdapter, localStorageQueue);
                localDuplicateVideoStorageAdapter = new TelegramDuplicateVideoStorageAdapter(localStorageAdapter, localStorageQueue);
                localHideReactionsStorage = new TelegramHideReactionsStorage(localStorageAdapter, localStorageQueue);
                localHideChannelEndAdvertisementStorage =
                        new TelegramHideChannelEndAdvertisementStorage(
                                localStorageAdapter, localStorageQueue);
                localHideFullscreenVideoAdvertisementStorage =
                        new TelegramHideFullscreenVideoAdvertisementStorage(
                                localStorageAdapter, localStorageQueue);
                localHideChannelPinnedMessageHeaderStorage =
                        new TelegramHideChannelPinnedMessageHeaderStorage(
                                localStorageAdapter, localStorageQueue);
                localMessageCtaButtonSettingsStorage = new TelegramMessageCtaButtonSettingsStorage(
                        localStorageAdapter, localStorageQueue);
                BootstrapConfiguration configuration =
                        BootstrapConfiguration.withRuleStorage(localStorageAdapter, localBlackListStorageAdapter,
                                localDuplicateVideoStorageAdapter);
                NoiseBootstrap.initialize(configuration);
                corePublished = NoiseBootstrap.isInitialized();
                if (!corePublished) {
                    throw new IllegalStateException("NoiseBootstrap did not publish persistent composition");
                }
                localWhiteListManagementGateway = new TelegramWhiteListManagementGateway(
                        NoiseBootstrap.getCore(), localStorageQueue);
                localBlackListManagementGateway = new TelegramBlackListManagementGateway(
                        NoiseBootstrap.getCore(), localStorageQueue);
                localHideReactionsGateway = new TelegramHideReactionsGateway(
                        localHideReactionsStorage, localStorageQueue);
                localHideReactionsGateway.startInitialLoad();
                localHideChannelEndAdvertisementSettingsGateway =
                        new TelegramHideChannelEndAdvertisementSettingsGateway(
                                localHideChannelEndAdvertisementStorage, localStorageQueue);
                localHideChannelEndAdvertisementSettingsGateway.startInitialLoad();
                localHideFullscreenVideoAdvertisementSettingsGateway =
                        new TelegramHideFullscreenVideoAdvertisementSettingsGateway(
                                localHideFullscreenVideoAdvertisementStorage, localStorageQueue);
                localHideFullscreenVideoAdvertisementSettingsGateway.startInitialLoad();
                localHideChannelPinnedMessageHeaderSettingsGateway =
                        new TelegramHideChannelPinnedMessageHeaderSettingsGateway(
                                localHideChannelPinnedMessageHeaderStorage, localStorageQueue);
                localHideChannelPinnedMessageHeaderSettingsGateway.startInitialLoad();
                localDuplicateVideoSettingsGateway = new TelegramDuplicateVideoSettingsGateway(
                        NoiseBootstrap.getCore(), localStorageQueue);
                localDuplicateVideoSettingsGateway.startInitialLoad();
                localMessageCtaButtonSettingsGateway = new TelegramMessageCtaButtonSettingsGateway(
                        localMessageCtaButtonSettingsStorage, localStorageQueue);
                localMessageCtaButtonSettingsGateway.startInitialLoad();

                storageQueue = localStorageQueue;
                duplicateVideoWorkerQueue = localDuplicateVideoWorkerQueue;
                storageAdapter = localStorageAdapter;
                blackListStorageAdapter = localBlackListStorageAdapter;
                whiteListManagementGateway = localWhiteListManagementGateway;
                blackListManagementGateway = localBlackListManagementGateway;
                hideReactionsGateway = localHideReactionsGateway;
                hideChannelEndAdvertisementSettingsGateway =
                        localHideChannelEndAdvertisementSettingsGateway;
                hideFullscreenVideoAdvertisementSettingsGateway =
                        localHideFullscreenVideoAdvertisementSettingsGateway;
                hideChannelPinnedMessageHeaderSettingsGateway =
                        localHideChannelPinnedMessageHeaderSettingsGateway;
                duplicateVideoSettingsGateway = localDuplicateVideoSettingsGateway;
                messageCtaButtonSettingsGateway = localMessageCtaButtonSettingsGateway;
                initialized = true;
            } catch (Throwable throwable) {
                FileLog.e(throwable);
                if (!corePublished) {
                    rollbackUnpublishedResources(localStorageQueue, localStorageAdapter);
                    recycleUnpublishedWorkerQueue(localDuplicateVideoWorkerQueue);
                }
            }
        }
    }

    public static synchronized TelegramWhiteListManagementGateway getWhiteListManagementGateway() {
        if (!initialized) {
            throw new IllegalStateException("Telegram Noise Bootstrap is not initialized");
        }
        if (whiteListManagementGateway == null) {
            throw new IllegalStateException("Telegram White List management gateway is not initialized");
        }
        return whiteListManagementGateway;
    }

    public static synchronized TelegramBlackListManagementGateway getBlackListManagementGateway() {
        if (!initialized || blackListManagementGateway == null) {
            throw new IllegalStateException("Telegram Black List management gateway is not initialized");
        }
        return blackListManagementGateway;
    }

    public static synchronized TelegramHideReactionsGateway getHideReactionsGateway() {
        if (!initialized || hideReactionsGateway == null) {
            throw new IllegalStateException("Telegram Hide Reactions gateway is not initialized");
        }
        return hideReactionsGateway;
    }

    public static synchronized TelegramHideChannelEndAdvertisementSettingsGateway
            getHideChannelEndAdvertisementSettingsGateway() {
        if (!initialized || hideChannelEndAdvertisementSettingsGateway == null) {
            throw new IllegalStateException(
                    "Telegram channel-end advertisement settings gateway is not initialized");
        }
        return hideChannelEndAdvertisementSettingsGateway;
    }

    public static synchronized TelegramHideFullscreenVideoAdvertisementSettingsGateway
            getHideFullscreenVideoAdvertisementSettingsGateway() {
        if (!initialized || hideFullscreenVideoAdvertisementSettingsGateway == null) {
            throw new IllegalStateException(
                    "Telegram fullscreen video advertisement settings gateway is not initialized");
        }
        return hideFullscreenVideoAdvertisementSettingsGateway;
    }

    public static synchronized TelegramHideChannelPinnedMessageHeaderSettingsGateway
            getHideChannelPinnedMessageHeaderSettingsGateway() {
        if (!initialized || hideChannelPinnedMessageHeaderSettingsGateway == null) {
            throw new IllegalStateException(
                    "Telegram channel pinned-message-header settings gateway is not initialized");
        }
        return hideChannelPinnedMessageHeaderSettingsGateway;
    }

    public static synchronized TelegramDuplicateVideoSettingsGateway getDuplicateVideoSettingsGateway() {
        if (!initialized || duplicateVideoSettingsGateway == null) {
            throw new IllegalStateException("Telegram Duplicate Video settings gateway is not initialized");
        }
        return duplicateVideoSettingsGateway;
    }

    public static synchronized TelegramMessageCtaButtonSettingsGateway
            getMessageCtaButtonSettingsGateway() {
        if (!initialized || messageCtaButtonSettingsGateway == null) {
            throw new IllegalStateException("Telegram message CTA button settings gateway is not initialized");
        }
        return messageCtaButtonSettingsGateway;
    }

    static synchronized DuplicateVideoCoordinator createDuplicateVideoCoordinator(
            DuplicateVideoPresentationConsumer presentationConsumer
    ) {
        if (!initialized || storageQueue == null || duplicateVideoWorkerQueue == null) {
            throw new IllegalStateException("Telegram Duplicate Video composition is not initialized");
        }
        return new DuplicateVideoCoordinator(
                NoiseBootstrap.getCore(), duplicateVideoWorkerQueue, storageQueue, presentationConsumer);
    }

    public static synchronized TelegramDuplicateVideoChatRuntime createDuplicateVideoChatRuntime(
            int accountId
    ) {
        TelegramDuplicateVideoPresentationApplier presentationApplier =
                new TelegramDuplicateVideoPresentationApplier(new TelegramCollapseState(),
                        TelegramNoiseBootstrap::isDuplicateVideoEnabled);
        return new TelegramDuplicateVideoChatRuntime(accountId,
                createDuplicateVideoCoordinator(presentationApplier), presentationApplier);
    }

    public static boolean shouldHideReactions() {
        TelegramHideReactionsGateway gateway = hideReactionsGateway;
        if (gateway == null) {
            return false;
        }
        try {
            return gateway.shouldHideReactions();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Read-only integration policy accessor for future channel-end ad presentation. */
    public static boolean isHideChannelEndAdvertisementEnabled() {
        if (!CleargramPersonalAdSuppressionPolicy.isAvailable()) {
            return false;
        }
        TelegramHideChannelEndAdvertisementSettingsGateway gateway =
                hideChannelEndAdvertisementSettingsGateway;
        return gateway != null && gateway.isHideChannelEndAdvertisementEnabled();
    }

    /** Read-only integration policy accessor for the future fullscreen video ad gate. */
    public static boolean shouldHideFullscreenVideoAdvertisement() {
        if (!CleargramPersonalAdSuppressionPolicy.isAvailable()) {
            return false;
        }
        TelegramHideFullscreenVideoAdvertisementSettingsGateway gateway =
                hideFullscreenVideoAdvertisementSettingsGateway;
        if (gateway == null) {
            return false;
        }
        try {
            return gateway.shouldHideFullscreenVideoAdvertisement();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Read-only integration policy accessor for the future channel pinned-header gate. */
    public static boolean shouldHideChannelPinnedMessageHeader() {
        TelegramHideChannelPinnedMessageHeaderSettingsGateway gateway =
                hideChannelPinnedMessageHeaderSettingsGateway;
        if (gateway == null) {
            return false;
        }
        try {
            return gateway.shouldHideChannelPinnedMessageHeader();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isDuplicateVideoEnabled() {
        TelegramDuplicateVideoSettingsGateway gateway = duplicateVideoSettingsGateway;
        return gateway != null && gateway.isDuplicateVideoEnabled();
    }

    /** Read-only fail-open snapshot for the future NG-015 presentation gate. */
    public static MessageCtaButtonSettingsState getMessageCtaButtonSettings() {
        TelegramMessageCtaButtonSettingsGateway gateway = messageCtaButtonSettingsGateway;
        if (gateway == null) {
            return MessageCtaButtonSettingsState.DEFAULT;
        }
        try {
            return gateway.getMessageCtaButtonSettings();
        } catch (RuntimeException ignored) {
            return MessageCtaButtonSettingsState.DEFAULT;
        }
    }

    private static void validateApplicationFilesDirectory(File applicationFilesDirectory) {
        if (applicationFilesDirectory == null) {
            throw new IllegalArgumentException("applicationFilesDirectory must not be null");
        }
        if (!applicationFilesDirectory.exists()) {
            throw new IllegalArgumentException("applicationFilesDirectory must exist");
        }
        if (!applicationFilesDirectory.isDirectory()) {
            throw new IllegalArgumentException("applicationFilesDirectory must be a directory");
        }
    }

    private static void rollbackUnpublishedResources(
            DispatchQueue localStorageQueue,
            TelegramWhiteListStorageAdapter localStorageAdapter
    ) {
        if (localStorageQueue == null) {
            return;
        }
        try {
            localStorageQueue.postRunnable(() -> {
                try {
                    if (localStorageAdapter != null) {
                        localStorageAdapter.close();
                    }
                } catch (Throwable throwable) {
                    FileLog.e(throwable);
                } finally {
                    try {
                        localStorageQueue.recycle();
                    } catch (Throwable throwable) {
                        FileLog.e(throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void recycleUnpublishedWorkerQueue(DispatchQueue localDuplicateVideoWorkerQueue) {
        if (localDuplicateVideoWorkerQueue == null) {
            return;
        }
        try {
            localDuplicateVideoWorkerQueue.recycle();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }
}
