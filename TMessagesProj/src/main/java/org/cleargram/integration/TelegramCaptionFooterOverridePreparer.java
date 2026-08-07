package org.cleargram.integration;

import org.telegram.messenger.MessageObject;

/** Stateless caption-only boundary before the accepted footer trim preparer. */
public final class TelegramCaptionFooterOverridePreparer {

    private TelegramCaptionFooterOverridePreparer() {
    }

    public static CharSequence prepare(MessageObject messageObject, int footerStartUtf16) {
        try {
            if (messageObject == null || messageObject.messageOwner == null
                    || messageObject.messageOwner.message == null || messageObject.caption == null
                    || !messageObject.messageOwner.message.equals(messageObject.caption.toString())) {
                return null;
            }
            TelegramChannelFooterTrimPreparer.Result result =
                    TelegramChannelFooterTrimPreparer.prepare(messageObject.caption, footerStartUtf16);
            return result.getStatus() == TelegramChannelFooterTrimPreparer.Status.SUCCESS ? result.getText() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
