package org.cleargram.integration;

import android.net.Uri;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/** Stateless classifier for explicit public Telegram URL entities in a channel footer. */
public final class TelegramChannelFooterLinkClassifier {

    static final class TrimResult {
        private static final TrimResult NO_MATCH = new TrimResult(-1, -1);

        final int startInclusive;
        final int endExclusive;

        private TrimResult(int startInclusive, int endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        boolean isMatch() {
            return startInclusive >= 0;
        }
    }

    interface PublicLinkMatcher {
        boolean hasPublicLink(TLRPC.Chat chat, String username);
    }

    public enum Kind {
        NONE,
        SELF_CHANNEL,
        FOREIGN_CHANNEL
    }

    public static final class Result {

        private static final Result NONE = new Result(Kind.NONE, -1);
        private static final Result FOREIGN_CHANNEL = new Result(Kind.FOREIGN_CHANNEL, -1);

        private final Kind kind;
        private final int footerStartUtf16;

        private Result(Kind kind, int footerStartUtf16) {
            this.kind = kind;
            this.footerStartUtf16 = footerStartUtf16;
        }

        public Kind getKind() {
            return kind;
        }

        public int getFooterStartUtf16() {
            return footerStartUtf16;
        }
    }

    private TelegramChannelFooterLinkClassifier() {
    }

    /**
     * NG-016 classifier entry: returns only a UTF-16 trim range or no match.
     * Kept package-private until a later presentation integration consumes it.
     */
    static TrimResult classifyLastLine(
            String text,
            java.util.List<TLRPC.MessageEntity> entities,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix
    ) {
        return classifyLastLine(text, entities, currentChat, runtimeLinkPrefix,
                ChatObject::hasPublicLink);
    }

    static TrimResult classifyLastLine(
            String text,
            java.util.List<TLRPC.MessageEntity> entities,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix,
            PublicLinkMatcher publicLinkMatcher
    ) {
        try {
            if (text == null || entities == null || currentChat == null
                    || !ChatObject.isChannelAndNotMegaGroup(currentChat)
                    || ChatObject.isForum(currentChat) || !hasPublicIdentity(currentChat)
                    || publicLinkMatcher == null) {
                return TrimResult.NO_MATCH;
            }
            LineRange line = findLastLogicalLine(text);
            if (line == null) {
                return TrimResult.NO_MATCH;
            }
            boolean hasSameChannelUrl = false;
            for (int i = 0; i < entities.size(); i++) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (entity == null) {
                    return TrimResult.NO_MATCH;
                }
                if (!(entity instanceof TLRPC.TL_messageEntityUrl)
                        && !(entity instanceof TLRPC.TL_messageEntityTextUrl)) {
                    continue;
                }
                if (!isSafeEntityRange(entity, text)) {
                    return TrimResult.NO_MATCH;
                }
                int entityEnd = entity.offset + entity.length;
                if (entityEnd <= line.start || entity.offset >= line.end) {
                    continue;
                }
                if (entity.offset < line.start || entityEnd > line.end) {
                    return TrimResult.NO_MATCH;
                }
                String url = entity instanceof TLRPC.TL_messageEntityTextUrl
                        ? entity.url : text.substring(entity.offset, entityEnd);
                TelegramPublicChannelUrlParser.Result parsed =
                        TelegramPublicChannelUrlParser.parse(url, runtimeLinkPrefix);
                if (parsed.kind == TelegramPublicChannelUrlParser.Kind.UNSAFE) {
                    return TrimResult.NO_MATCH;
                }
                if (parsed.kind == TelegramPublicChannelUrlParser.Kind.PUBLIC_USERNAME
                        && publicLinkMatcher.hasPublicLink(currentChat, parsed.username)) {
                    hasSameChannelUrl = true;
                }
            }
            return hasSameChannelUrl ? new TrimResult(line.trimStart, text.length()) : TrimResult.NO_MATCH;
        } catch (RuntimeException ignored) {
            return TrimResult.NO_MATCH;
        }
    }

    public static Result classify(
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages,
            TLRPC.Chat currentChat
    ) {
        try {
            if (messageObject == null || groupedMessages != null || messageObject.messageOwner == null) {
                return Result.NONE;
            }
            TrimResult result = classifyLastLine(messageObject.messageOwner.message,
                    messageObject.messageOwner.entities, currentChat, null);
            return result.isMatch() ? new Result(Kind.SELF_CHANNEL, result.startInclusive) : Result.NONE;
        } catch (RuntimeException ignored) {
            return Result.NONE;
        }
    }

    private static boolean isValidEntityRange(TLRPC.MessageEntity entity, int textLength) {
        return entity.offset >= 0
                && entity.length > 0
                && entity.offset <= textLength - entity.length;
    }

    private static boolean isSafeEntityRange(TLRPC.MessageEntity entity, String text) {
        if (!isValidEntityRange(entity, text.length())) {
            return false;
        }
        int end = entity.offset + entity.length;
        return !splitsSurrogatePair(text, entity.offset) && !splitsSurrogatePair(text, end);
    }

    private static boolean splitsSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset));
    }

    private static boolean hasPublicIdentity(TLRPC.Chat chat) {
        if (chat.username != null && !chat.username.isEmpty()) {
            return true;
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); i++) {
                TLRPC.TL_username username = chat.usernames.get(i);
                if (username != null && username.active && username.username != null && !username.username.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LineRange findLastLogicalLine(String text) {
        int end = text.length();
        while (end > 0) {
            char character = text.charAt(end - 1);
            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                break;
            }
            end--;
        }
        if (end == 0) {
            return null;
        }
        int start = end;
        while (start > 0) {
            char character = text.charAt(start - 1);
            if (character == '\r' || character == '\n') {
                break;
            }
            start--;
        }
        int trimStart = start;
        while (trimStart > 0) {
            char character = text.charAt(trimStart - 1);
            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                break;
            }
            trimStart--;
        }
        return new LineRange(start, end, trimStart);
    }

    private static final class LineRange {
        final int start;
        final int end;
        final int trimStart;

        LineRange(int start, int end, int trimStart) {
            this.start = start;
            this.end = end;
            this.trimStart = trimStart;
        }
    }

    private static boolean isInsideFooter(TLRPC.MessageEntity entity, int footerStart, int footerEnd) {
        return entity.offset >= footerStart && entity.offset + entity.length <= footerEnd;
    }

    private static boolean isOutsideFooter(TLRPC.MessageEntity entity, int footerStart, int footerEnd) {
        int entityEnd = entity.offset + entity.length;
        return entityEnd <= footerStart || entity.offset >= footerEnd;
    }

    private static UrlClassification classifyPublicUsernameUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }
        if (rawUrl.regionMatches(true, 0, "tg:", 0, "tg:".length())) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }

        String normalizedUrl = rawUrl.contains("://") ? rawUrl : "https://" + rawUrl;
        Uri uri;
        try {
            uri = Uri.parse(normalizedUrl);
            if (!Browser.isInternalUrl(normalizedUrl, new boolean[1])) {
                return UrlClassification.NOT_TELEGRAM_URL;
            }
        } catch (RuntimeException ignored) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }

        String host = uri.getHost();
        if (host == null) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }
        host = host.toLowerCase(Locale.US);
        if (!("t.me".equals(host) || "telegram.me".equals(host) || "telegram.dog".equals(host))) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }

        java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() != 1 && segments.size() != 2) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }
        String username = segments.get(0);
        if (!isPublicUsername(username) || isReservedPath(username)) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }
        if (segments.size() == 2 && !isPostId(segments.get(1))) {
            return UrlClassification.UNSUPPORTED_TELEGRAM_URL;
        }
        return new UrlClassification(username);
    }

    private static boolean isPublicUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]+");
    }

    private static boolean isReservedPath(String username) {
        return "c".equalsIgnoreCase(username)
                || "joinchat".equalsIgnoreCase(username)
                || "share".equalsIgnoreCase(username)
                || "msg".equalsIgnoreCase(username)
                || "proxy".equalsIgnoreCase(username)
                || "socks".equalsIgnoreCase(username)
                || "iv".equalsIgnoreCase(username);
    }

    private static boolean isPostId(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class UrlClassification {

        private static final UrlClassification NOT_TELEGRAM_URL = new UrlClassification(null);
        private static final UrlClassification UNSUPPORTED_TELEGRAM_URL = new UrlClassification(null);

        private final String username;

        private UrlClassification(String username) {
            this.username = username;
        }
    }
}
