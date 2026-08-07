package org.telegram.ui.Cells;

import android.text.SpannableString;
import android.text.SpannedString;

import org.telegram.messenger.MessageObject;

/** Transient, cell-local caption override state for the current bind only. */
final class TelegramCaptionOverrideState {
    private MessageObject pendingMessage;
    private String pendingSource;
    private CharSequence pendingCaption;
    private MessageObject activeMessage;
    private String activeSource;
    private SpannableString activeCaption;

    void setPending(MessageObject message, CharSequence source, CharSequence caption) {
        clearPending();
        try {
            if (message != null && source != null && caption != null) {
                pendingMessage = message;
                pendingSource = source.toString();
                pendingCaption = new SpannedString(caption);
            }
        } catch (RuntimeException ignored) {
            clearPending();
        }
    }

    void clearPending() {
        pendingMessage = null;
        pendingSource = null;
        pendingCaption = null;
    }

    void consumeForBind(MessageObject message) {
        MessageObject expected = pendingMessage;
        String source = pendingSource;
        CharSequence caption = pendingCaption;
        clearPending();
        clearActive();
        try {
            if (expected == message && message != null && message.caption != null
                    && source != null && source.equals(message.caption.toString()) && caption != null) {
                activeMessage = message;
                activeSource = source;
                activeCaption = new SpannableString(caption);
            }
        } catch (RuntimeException ignored) {
            clearActive();
        }
    }

    CharSequence getEffectiveCaption(MessageObject message) {
        CharSequence original = message == null ? null : message.caption;
        try {
            if (activeMessage == message && original != null && activeSource != null
                    && activeSource.equals(original.toString()) && activeCaption != null) {
                return activeCaption;
            }
        } catch (RuntimeException ignored) {
        }
        clearActive();
        return original;
    }

    void reset() {
        clearPending();
        clearActive();
    }

    private void clearActive() {
        activeMessage = null;
        activeSource = null;
        activeCaption = null;
    }
}
