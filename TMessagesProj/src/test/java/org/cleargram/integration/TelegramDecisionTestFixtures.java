package org.cleargram.integration;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
final class TelegramDecisionTestFixtures {

    private TelegramDecisionTestFixtures() {
    }

    static TestMessage message(int accountId, long dialogId, long topicId, int messageId) {
        return new TestMessage(accountId, dialogId, topicId, messageId);
    }

    static TestCell cell(MessageObject message) {
        return new TestCell(message);
    }

    static final class TestMessage extends MessageObject {

        long dialogId;
        long topicId;
        int messageId;

        private TestMessage(int accountId, long dialogId, long topicId, int messageId) {
            super(accountId, (TLRPC.Message) null, "", null, null, false, false, false, false);
            this.dialogId = dialogId;
            this.topicId = topicId;
            this.messageId = messageId;
        }

        @Override
        public long getDialogId() {
            return dialogId;
        }

        @Override
        public long getTopicId() {
            return topicId;
        }

        @Override
        public int getId() {
            return messageId;
        }
    }

    static final class TestCell implements TelegramDecisionContext.PresentationTarget {

        MessageObject message;
        boolean compact;
        boolean hidden;
        int resetCalls;
        Runnable expansionCallback;

        private TestCell(MessageObject message) {
            this.message = message;
        }

        @Override
        public MessageObject getMessageObject() {
            return message;
        }

        @Override
        public void applyCompactPresentation(CharSequence text, Runnable callback) {
            compact = true;
            hidden = false;
            expansionCallback = callback;
        }

        @Override
        public void applyHiddenPresentation() {
            compact = false;
            hidden = true;
            expansionCallback = null;
        }

        @Override
        public void resetPresentation() {
            compact = false;
            hidden = false;
            expansionCallback = null;
            resetCalls++;
        }
    }
}
