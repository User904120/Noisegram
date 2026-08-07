package org.cleargram.integration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.cleargram.spi.DuplicateVideoReadableSource;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Synchronous local-file resolver; filesystem checks must not run on a UI hot path. */
final class TelegramDuplicateVideoSourceResolver {

    interface FinalLocalPathLookup { File resolve(int account, MessageObject messageObject); }
    enum SupportedMediaKind { NORMAL_VIDEO, VIDEO_BACKED_GIF, UNSUPPORTED }

    private final FinalLocalPathLookup finalLocalPathLookup;

    TelegramDuplicateVideoSourceResolver() {
        finalLocalPathLookup = null;
    }

    TelegramDuplicateVideoSourceResolver(FinalLocalPathLookup finalLocalPathLookup) {
        this.finalLocalPathLookup = Objects.requireNonNull(finalLocalPathLookup, "finalLocalPathLookup");
    }

    TelegramDuplicateVideoSourceResolution resolve(int account, MessageObject messageObject) {
        Objects.requireNonNull(messageObject, "messageObject");
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        if (!(media instanceof TLRPC.TL_messageMediaDocument)) {
            return TelegramDuplicateVideoSourceResolution.unsupported();
        }
        TLRPC.Document document = media.document;
        SupportedMediaKind kind = classifySupportedMedia(messageObject, document);
        if (kind == SupportedMediaKind.UNSUPPORTED) return TelegramDuplicateVideoSourceResolution.unsupported();
        long expectedSize = document.size;
        if (expectedSize <= 0) return TelegramDuplicateVideoSourceResolution.localUnavailable();
        final File file;
        try {
            file = finalLocalPathLookup == null
                    ? resolveCanonicalFinalPath(media, document, kind)
                    : finalLocalPathLookup.resolve(account, messageObject);
        } catch (RuntimeException error) {
            return TelegramDuplicateVideoSourceResolution.localUnavailable();
        }
        if (file == null) return TelegramDuplicateVideoSourceResolution.localUnavailable();
        try {
            TelegramLocalVideoReadableSource.verifyFile(file, expectedSize);
        } catch (IOException error) {
            return TelegramDuplicateVideoSourceResolution.localUnavailable();
        }
        DuplicateVideoReadableSource source = new TelegramLocalVideoReadableSource(file, expectedSize);
        return TelegramDuplicateVideoSourceResolution.localAvailable(source);
    }

    TelegramDuplicateVideoSourceResolution resolve(
            TelegramDuplicateVideoMediaSourceDescriptor descriptor
    ) {
        Objects.requireNonNull(descriptor, "descriptor");
        try {
            TelegramLocalVideoReadableSource.verifyFile(
                    descriptor.getFile(), descriptor.getExpectedSize());
        } catch (IOException | RuntimeException error) {
            return TelegramDuplicateVideoSourceResolution.localUnavailable();
        }
        return TelegramDuplicateVideoSourceResolution.localAvailable(
                new TelegramLocalVideoReadableSource(
                        descriptor.getFile(), descriptor.getExpectedSize()));
    }

    private static SupportedMediaKind classifySupportedMedia(
            MessageObject messageObject,
            TLRPC.Document document
    ) {
        if (document == null || messageObject.isRoundVideo() || messageObject.isVoice()
                || messageObject.isMusic()) return SupportedMediaKind.UNSUPPORTED;
        if (MessageObject.isVideoDocument(document)) return SupportedMediaKind.NORMAL_VIDEO;
        if (!MessageObject.isNewGifDocument(document)) return SupportedMediaKind.UNSUPPORTED;
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return SupportedMediaKind.VIDEO_BACKED_GIF;
            }
        }
        return SupportedMediaKind.UNSUPPORTED;
    }

    private static File resolveCanonicalFinalPath(
            TLRPC.MessageMedia media,
            TLRPC.Document document,
            SupportedMediaKind kind
    ) {
        int directoryType = canonicalDirectoryType(media, document, kind);
        File directory = FileLoader.checkDirectory(directoryType);
        String filename = FileLoader.getAttachFileName(document);
        if (directory == null || filename == null || filename.length() == 0) return null;
        return new File(directory, filename);
    }

    static int canonicalDirectoryType(
            TLRPC.MessageMedia media,
            TLRPC.Document document,
            SupportedMediaKind kind
    ) {
        if (document.key != null || media.ttl_seconds != 0) return FileLoader.MEDIA_DIR_CACHE;
        return kind == SupportedMediaKind.VIDEO_BACKED_GIF
                ? FileLoader.MEDIA_DIR_DOCUMENT : FileLoader.MEDIA_DIR_VIDEO;
    }
}
