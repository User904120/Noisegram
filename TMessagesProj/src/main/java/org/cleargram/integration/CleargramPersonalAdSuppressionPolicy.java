package org.cleargram.integration;

import org.telegram.messenger.BuildConfig;

/** Build-time availability boundary for personal advertisement-suppression controls. */
public final class CleargramPersonalAdSuppressionPolicy {

    private CleargramPersonalAdSuppressionPolicy() {
    }

    public static boolean isAvailable() {
        return BuildConfig.CLEARGRAM_PERSONAL_AD_SUPPRESSION;
    }
}
