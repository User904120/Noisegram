package org.cleargram.integration;

import android.util.Log;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;

/** Stateless, fail-open diagnostics for message-wide replacement decisions. */
final class TelegramDecisionDiagnostics {

    static final String TAG = "CleargramDecision";

    private static final int PREVIEW_LIMIT = 120;

    private TelegramDecisionDiagnostics() {
    }

    static void log(
            NoiseDecision decision,
            TelegramDecisionContext context,
            boolean grouped,
            String source,
            boolean ctaMatched
    ) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        String entry = format(decision, context, grouped, source, ctaMatched);
        if (entry == null) {
            return;
        }
        try {
            Log.d(TAG, entry);
        } catch (Throwable ignored) {
            // Diagnostics must never affect message presentation.
        }
    }

    static String format(
            NoiseDecision decision,
            TelegramDecisionContext context,
            boolean grouped,
            String source,
            boolean ctaMatched
    ) {
        NoiseAction action = decision != null ? decision.getAction() : null;
        if (action != NoiseAction.HIDE && action != NoiseAction.COLLAPSE) {
            return null;
        }
        MessageObject messageObject = context != null ? context.getDecisionMessage() : null;
        boolean hasIdentity = context != null && context.hasDecisionIdentity();
        String account = hasIdentity ? String.valueOf(context.getDecisionAccountId()) : "unknown";
        String dialog = hasIdentity ? String.valueOf(context.getDecisionDialogId()) : "unknown";
        String message = hasIdentity ? String.valueOf(context.getDecisionMessageId()) : "unknown";
        boolean sponsored = isSponsored(messageObject);
        return "account=" + account
                + " dialog=" + dialog
                + " message=" + message
                + " grouped=" + grouped
                + " action=" + action
                + " source=" + normalizeSource(source)
                + " ctaMatched=" + ctaMatched
                + " sponsored=" + sponsored
                + " preview=" + safePreview(messageObject != null ? messageObject.messageText : null);
    }

    static String safePreview(CharSequence text) {
        if (text == null) {
            return "";
        }
        String preview = text.toString()
                .replaceAll("(?i)(?:https?://|tg://|www\\.)\\S+", "[url]")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return preview.length() <= PREVIEW_LIMIT ? preview : preview.substring(0, PREVIEW_LIMIT);
    }

    private static boolean isSponsored(MessageObject messageObject) {
        try {
            return messageObject != null && messageObject.isSponsored();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String normalizeSource(String source) {
        if ("BLACK_LIST".equals(source)
                || "CTA_BUTTON".equals(source)
                || "DUPLICATE_VIDEO".equals(source)
                || "CORE_OTHER".equals(source)) {
            return source;
        }
        return "UNKNOWN";
    }
}
