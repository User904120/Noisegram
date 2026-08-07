package org.cleargram.integration;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Stateless structural classifier for the confirmed NG-015 CTA shape. */
public final class TelegramMessageCtaClassifier {

    private TelegramMessageCtaClassifier() {
    }

    public static boolean hasTargetCtaButton(
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages
    ) {
        try {
            return hasTargetCtaButton(createInput(messageObject, groupedMessages));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static MessageCtaClassificationInput createInput(
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages
    ) {
        return new MessageCtaClassificationInput(
                messageObject != null && messageObject.messageOwner != null
                        ? messageObject.messageOwner.reply_markup : null,
                messageObject != null && messageObject.isSponsored(),
                groupedMessages != null
        );
    }

    static boolean hasTargetCtaButton(MessageCtaClassificationInput input) {
        try {
            if (input == null
                    || input.sponsored
                    || input.grouped
                    || !(input.replyMarkup instanceof TLRPC.TL_replyInlineMarkup)) {
                return false;
            }
            TLRPC.TL_replyInlineMarkup replyMarkup =
                    (TLRPC.TL_replyInlineMarkup) input.replyMarkup;
            if (replyMarkup.rows == null || replyMarkup.rows.isEmpty()) {
                return false;
            }
            int urlButtonCount = 0;
            for (TLRPC.TL_keyboardButtonRow row : replyMarkup.rows) {
                if (row == null || row.buttons == null || row.buttons.isEmpty()) {
                    return false;
                }
                for (TLRPC.KeyboardButton button : row.buttons) {
                    if (!(button instanceof TLRPC.TL_keyboardButtonUrl)) {
                        return false;
                    }
                    urlButtonCount++;
                }
            }
            return urlButtonCount > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
