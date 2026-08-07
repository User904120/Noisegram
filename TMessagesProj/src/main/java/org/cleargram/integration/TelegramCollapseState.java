package org.cleargram.integration;

import java.util.HashSet;
import java.util.Set;

/**
 * Chat-scoped expansion markers for Cleargram-owned message effects.
 */
public final class TelegramCollapseState {

    private final Set<MessageKey> expandedMessages = new HashSet<>();

    boolean isExpanded(TelegramDecisionContext context) {
        MessageKey key = getMessageKey(context);
        return key != null && expandedMessages.contains(key);
    }

    void markExpanded(TelegramDecisionContext context) {
        MessageKey key = getMessageKey(context);
        if (key != null) {
            expandedMessages.add(key);
        }
    }

    void remove(TelegramDecisionContext context) {
        MessageKey key = getMessageKey(context);
        if (key != null) {
            expandedMessages.remove(key);
        }
    }

    public void clear() {
        expandedMessages.clear();
    }

    private MessageKey getMessageKey(TelegramDecisionContext context) {
        if (context == null || !context.hasDecisionIdentity()) {
            return null;
        }
        return new MessageKey(
                context.getDecisionAccountId(),
                context.getDecisionDialogId(),
                context.getDecisionTopicId(),
                context.getDecisionMessageId()
        );
    }

    private final class MessageKey {

        private final int accountId;
        private final long dialogId;
        private final long topicId;
        private final int messageId;

        private MessageKey(int accountId, long dialogId, long topicId, int messageId) {
            this.accountId = accountId;
            this.dialogId = dialogId;
            this.topicId = topicId;
            this.messageId = messageId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            MessageKey key = (MessageKey) object;
            return accountId == key.accountId
                    && dialogId == key.dialogId
                    && topicId == key.topicId
                    && messageId == key.messageId;
        }

        @Override
        public int hashCode() {
            int result = accountId;
            result = 31 * result + (int) (dialogId ^ (dialogId >>> 32));
            result = 31 * result + (int) (topicId ^ (topicId >>> 32));
            result = 31 * result + messageId;
            return result;
        }
    }
}
