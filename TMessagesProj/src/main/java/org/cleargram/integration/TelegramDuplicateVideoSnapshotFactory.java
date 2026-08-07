package org.cleargram.integration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.cleargram.api.NoiseMessage;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Builds immutable Duplicate Video input from currently bound Telegram metadata only. */
final class TelegramDuplicateVideoSnapshotFactory {

    static final class Snapshot {
        private final LogicalPresentationId logicalPresentationId;
        private final DuplicateVideoCoordinatorSnapshot coordinatorSnapshot;

        private Snapshot(LogicalPresentationId logicalPresentationId,
                DuplicateVideoCoordinatorSnapshot coordinatorSnapshot) {
            this.logicalPresentationId = logicalPresentationId;
            this.coordinatorSnapshot = coordinatorSnapshot;
        }

        LogicalPresentationId getLogicalPresentationId() { return logicalPresentationId; }
        DuplicateVideoCoordinatorSnapshot getCoordinatorSnapshot() { return coordinatorSnapshot; }
    }

    private final TelegramMessageMapper messageMapper = new TelegramMessageMapper();

    Snapshot create(int accountId, MessageObject message, MessageObject.GroupedMessages groupedMessages) {
        if (message == null) return null;
        List<MessageObject> orderedMessages;
        LogicalPresentationId logicalPresentationId;
        if (groupedMessages != null && groupedMessages.messages != null
                && groupedMessages.messages.size() > 1
                && groupedMessages.messages.contains(message)) {
            orderedMessages = new ArrayList<>(groupedMessages.messages);
            logicalPresentationId = LogicalPresentationId.groupedMessage(accountId,
                    message.getDialogId(), message.getTopicId(), message.getGroupId());
        } else {
            orderedMessages = new ArrayList<>(1);
            orderedMessages.add(message);
            logicalPresentationId = LogicalPresentationId.singleMessage(accountId,
                    message.getDialogId(), message.getTopicId(), message.getId());
        }
        List<DuplicateVideoCoordinatorSnapshot.Item> items = new ArrayList<>();
        boolean hasOtherVisibleMedia = false;
        for (MessageObject orderedMessage : orderedMessages) {
            TelegramDuplicateVideoMediaSourceDescriptor descriptor = descriptorFor(orderedMessage);
            if (descriptor == null) {
                hasOtherVisibleMedia = true;
                continue;
            }
            items.add(new DuplicateVideoCoordinatorSnapshot.Item(
                    orderedMessage.getId(), descriptor, items.size()));
        }
        if (items.isEmpty()) return null;
        NoiseMessage logicalMessage = orderedMessages.size() == 1
                ? messageMapper.map(message)
                : messageMapper.mapGrouped(orderedMessages.get(0), orderedMessages);
        return new Snapshot(logicalPresentationId, new DuplicateVideoCoordinatorSnapshot(
                items, logicalMessage.getText(), hasOtherVisibleMedia));
    }

    private TelegramDuplicateVideoMediaSourceDescriptor descriptorFor(MessageObject message) {
        try {
            TLRPC.MessageMedia media = MessageObject.getMedia(message.messageOwner);
            if (!(media instanceof TLRPC.TL_messageMediaDocument) || media.document == null
                    || message.isRoundVideo() || message.isVoice() || message.isMusic()) return null;
            TLRPC.Document document = media.document;
            TelegramDuplicateVideoSourceResolver.SupportedMediaKind kind;
            if (MessageObject.isVideoDocument(document)) {
                kind = TelegramDuplicateVideoSourceResolver.SupportedMediaKind.NORMAL_VIDEO;
            } else if (MessageObject.isNewGifDocument(document) && hasVideoAttribute(document)) {
                kind = TelegramDuplicateVideoSourceResolver.SupportedMediaKind.VIDEO_BACKED_GIF;
            } else return null;
            if (document.size <= 0) return null;
            File directory = FileLoader.checkDirectory(
                    TelegramDuplicateVideoSourceResolver.canonicalDirectoryType(media, document, kind));
            String fileName = FileLoader.getAttachFileName(document);
            if (directory == null || fileName == null || fileName.length() == 0) return null;
            return new TelegramDuplicateVideoMediaSourceDescriptor(new File(directory, fileName), document.size);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasVideoAttribute(TLRPC.Document document) {
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) return true;
        }
        return false;
    }
}
