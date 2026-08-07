package org.cleargram.integration;

import org.cleargram.api.NoiseMessage;
import org.telegram.messenger.MessageObject;

import java.util.List;

/**
 * Converts Telegram message objects into Core-owned message models.
 */
public final class TelegramMessageMapper {

    public NoiseMessage map(MessageObject messageObject) {
        return new NoiseMessage(extractText(messageObject));
    }

    /**
     * Maps a grouped-media publication while retaining its canonical message
     * identity at the caller. The Core model currently contains text only.
     */
    public NoiseMessage mapGrouped(MessageObject canonicalMessage, List<MessageObject> groupMessages) {
        if (groupMessages == null || groupMessages.isEmpty()) {
            return new NoiseMessage("");
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < groupMessages.size(); i++) {
            String fragment = extractText(groupMessages.get(i));
            if (fragment.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(fragment);
        }
        return new NoiseMessage(text.toString());
    }

    private String extractText(MessageObject messageObject) {
        if (messageObject == null) {
            return "";
        }
        if (messageObject.caption != null) {
            return messageObject.caption.toString();
        }
        if (messageObject.messageText != null) {
            return messageObject.messageText.toString();
        }
        if (messageObject.messageOwner != null && messageObject.messageOwner.message != null) {
            return messageObject.messageOwner.message;
        }
        return "";
    }
}
