package org.cleargram.integration;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.Objects;

/**
 * Current Telegram-side context for applying one Cleargram decision.
 */
public final class TelegramDecisionContext {

    public enum PresentationRole {
        SINGLE,
        GROUP_OWNER,
        GROUP_MEMBER
    }

    private final MessageIdentity decisionIdentity;
    private final MessageIdentity boundIdentity;
    private final MessageObject decisionMessage;
    private final MessageObject boundMessage;
    private final ChatMessageCell messageCell;
    private final PresentationTarget presentationTarget;
    private final PresentationRole presentationRole;
    private final Runnable presentationUnitRebindCallback;

    public TelegramDecisionContext(int accountId, MessageObject messageObject, ChatMessageCell messageCell, Runnable localRebindCallback) {
        this(accountId, messageObject, messageObject, messageCell, PresentationRole.SINGLE, localRebindCallback);
    }

    public TelegramDecisionContext(
            int accountId,
            MessageObject decisionMessage,
            MessageObject boundMessage,
            ChatMessageCell messageCell,
            PresentationRole presentationRole,
            Runnable presentationUnitRebindCallback
    ) {
        this(
                accountId,
                decisionMessage,
                boundMessage,
                messageCell,
                new ChatMessageCellPresentationTarget(messageCell),
                presentationRole,
                presentationUnitRebindCallback
        );
    }

    TelegramDecisionContext(
            int accountId,
            MessageObject decisionMessage,
            MessageObject boundMessage,
            PresentationTarget presentationTarget,
            PresentationRole presentationRole,
            Runnable presentationUnitRebindCallback
    ) {
        this(
                accountId,
                decisionMessage,
                boundMessage,
                null,
                presentationTarget,
                presentationRole,
                presentationUnitRebindCallback
        );
    }

    private TelegramDecisionContext(
            int accountId,
            MessageObject decisionMessage,
            MessageObject boundMessage,
            ChatMessageCell messageCell,
            PresentationTarget presentationTarget,
            PresentationRole presentationRole,
            Runnable presentationUnitRebindCallback
    ) {
        this.decisionMessage = decisionMessage;
        this.boundMessage = boundMessage;
        this.messageCell = messageCell;
        this.presentationTarget = presentationTarget;
        this.presentationRole = Objects.requireNonNull(presentationRole, "presentationRole");
        this.presentationUnitRebindCallback = presentationUnitRebindCallback;
        this.decisionIdentity = MessageIdentity.from(accountId, decisionMessage);
        this.boundIdentity = MessageIdentity.from(accountId, boundMessage);
    }

    public ChatMessageCell getMessageCell() {
        return messageCell;
    }

    PresentationTarget getPresentationTarget() {
        return presentationTarget;
    }

    MessageObject getDecisionMessage() {
        return decisionMessage;
    }

    MessageObject getBoundMessage() {
        return boundMessage;
    }

    PresentationRole getPresentationRole() {
        return presentationRole;
    }

    boolean hasDecisionIdentity() {
        return decisionIdentity != null;
    }

    int getDecisionAccountId() {
        return decisionIdentity.accountId;
    }

    long getDecisionDialogId() {
        return decisionIdentity.dialogId;
    }

    long getDecisionTopicId() {
        return decisionIdentity.topicId;
    }

    int getDecisionMessageId() {
        return decisionIdentity.messageId;
    }

    public boolean isCurrent() {
        if (boundIdentity == null || boundMessage == null || presentationTarget == null) {
            return false;
        }
        try {
            MessageObject currentMessage = presentationTarget.getMessageObject();
            return boundIdentity.matches(currentMessage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    interface PresentationTarget {
        MessageObject getMessageObject();

        void applyCompactPresentation(CharSequence text, Runnable expansionCallback);

        void applyHiddenPresentation();

        void resetPresentation();
    }

    private static final class ChatMessageCellPresentationTarget implements PresentationTarget {

        private final ChatMessageCell messageCell;

        private ChatMessageCellPresentationTarget(ChatMessageCell messageCell) {
            this.messageCell = messageCell;
        }

        @Override
        public MessageObject getMessageObject() {
            return messageCell != null ? messageCell.getMessageObject() : null;
        }

        @Override
        public void applyCompactPresentation(CharSequence text, Runnable expansionCallback) {
            if (messageCell != null) {
                messageCell.applyCleargramCompactPresentation(text, expansionCallback);
            }
        }

        @Override
        public void applyHiddenPresentation() {
            if (messageCell != null) {
                messageCell.applyCleargramHiddenPresentation();
            }
        }

        @Override
        public void resetPresentation() {
            if (messageCell != null) {
                messageCell.resetCleargramVisualEffect();
            }
        }
    }

    boolean requestPresentationUnitRebind() {
        if (presentationUnitRebindCallback == null) {
            return false;
        }
        try {
            presentationUnitRebindCallback.run();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MessageIdentity {

        private final int accountId;
        private final long dialogId;
        private final long topicId;
        private final int messageId;

        private MessageIdentity(int accountId, long dialogId, long topicId, int messageId) {
            this.accountId = accountId;
            this.dialogId = dialogId;
            this.topicId = topicId;
            this.messageId = messageId;
        }

        static MessageIdentity from(int accountId, MessageObject messageObject) {
            if (messageObject == null) {
                return null;
            }
            try {
                return new MessageIdentity(
                        accountId,
                        messageObject.getDialogId(),
                        messageObject.getTopicId(),
                        messageObject.getId()
                );
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean matches(MessageObject messageObject) {
            if (messageObject == null) {
                return false;
            }
            try {
                return messageObject.currentAccount == accountId
                        && messageObject.getDialogId() == dialogId
                        && messageObject.getTopicId() == topicId
                        && messageObject.getId() == messageId;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
