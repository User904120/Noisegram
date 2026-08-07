package org.cleargram.integration;

import java.util.Objects;

/** Immutable Telegram-free identity of one logical presentation unit. */
final class LogicalPresentationId {

    enum Kind {
        SINGLE_MESSAGE,
        GROUPED_MESSAGE
    }

    private final Kind kind;
    private final int accountId;
    private final long dialogId;
    private final long topicId;
    private final long messageOrGroupId;

    private LogicalPresentationId(
            Kind kind,
            int accountId,
            long dialogId,
            long topicId,
            long messageOrGroupId
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (messageOrGroupId == 0) {
            throw new IllegalArgumentException("messageOrGroupId must not be zero");
        }
        this.accountId = accountId;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.messageOrGroupId = messageOrGroupId;
    }

    static LogicalPresentationId singleMessage(
            int accountId,
            long dialogId,
            long topicId,
            long messageId
    ) {
        return new LogicalPresentationId(
                Kind.SINGLE_MESSAGE, accountId, dialogId, topicId, messageId);
    }

    static LogicalPresentationId groupedMessage(
            int accountId,
            long dialogId,
            long topicId,
            long groupId
    ) {
        return new LogicalPresentationId(
                Kind.GROUPED_MESSAGE, accountId, dialogId, topicId, groupId);
    }

    Kind getKind() {
        return kind;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LogicalPresentationId)) {
            return false;
        }
        LogicalPresentationId other = (LogicalPresentationId) object;
        return kind == other.kind
                && accountId == other.accountId
                && dialogId == other.dialogId
                && topicId == other.topicId
                && messageOrGroupId == other.messageOrGroupId;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + accountId;
        result = 31 * result + (int) (dialogId ^ (dialogId >>> 32));
        result = 31 * result + (int) (topicId ^ (topicId >>> 32));
        result = 31 * result + (int) (messageOrGroupId ^ (messageOrGroupId >>> 32));
        return result;
    }
}
