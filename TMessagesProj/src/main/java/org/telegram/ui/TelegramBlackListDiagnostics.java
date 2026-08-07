package org.telegram.ui;

import android.util.Log;

import org.telegram.messenger.BuildVars;

/** Stateless integration-side trace for an actual terminal Black List decision. */
final class TelegramBlackListDiagnostics {

    static final String TAG = "CleargramBlackList";
    private TelegramBlackListDiagnostics() {
    }

    static void logMappedMetadata(
            boolean grouped,
            int mappedTextLength,
            int presentationUnitCount
    ) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        try {
            Log.i(TAG, formatMappedMetadata(grouped, mappedTextLength, presentationUnitCount));
        } catch (Throwable ignored) {
            // Diagnostics must not affect the Telegram presentation path.
        }
    }

    static String formatMappedMetadata(
            boolean grouped,
            int mappedTextLength,
            int presentationUnitCount
    ) {
        long threadId = Thread.currentThread().getId();
        return "phase=mapped threadId=" + threadId
                + " grouped=" + grouped
                + " mappedTextLength=" + mappedTextLength
                + " presentationUnitCount=" + presentationUnitCount;
    }
}
