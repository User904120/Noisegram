package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Composes classified channel-footer links into the existing Telegram decision path. */
public final class TelegramChannelFooterLinkPresentationPolicy {

    public enum Kind {
        NONE,
        FOREIGN_HIDE,
        SELF_TRIM
    }

    public static final class Result {
        private static final Result NONE = new Result(Kind.NONE, -1);
        private static final Result FOREIGN_HIDE = new Result(Kind.FOREIGN_HIDE, -1);

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

    private TelegramChannelFooterLinkPresentationPolicy() {
    }

    public static Result resolve(
            NoisePipelineOutcome outcome,
            NoiseAction existingPresentationAction,
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix
    ) {
        try {
            if (outcome != NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO || existingPresentationAction != null
                    || messageObject == null || groupedMessages != null || messageObject.isSponsored()
                    || messageObject.messageOwner == null || messageObject.messageOwner.media == null
                    || messageObject.caption == null || !messageObject.messageOwner.message.equals(messageObject.caption.toString())) {
                return Result.NONE;
            }
            TelegramChannelFooterLinkClassifier.TrimResult result =
                    TelegramChannelFooterLinkClassifier.classifyLastLine(
                            messageObject.messageOwner.message, messageObject.messageOwner.entities,
                            currentChat, runtimeLinkPrefix);
            if (result.isMatch()) {
                return new Result(Kind.SELF_TRIM, result.startInclusive);
            }
        } catch (RuntimeException ignored) {
            // Fail-open: preserve the existing presentation decision.
        }
        return Result.NONE;
    }

    /** Returns a bind-local request for the existing hidden cell presentation, never a Core decision. */
    public static boolean isFooterOnlyTextSuppressionRequested(
            NoisePipelineOutcome outcome,
            NoiseAction ctaAction,
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix
    ) {
        return TelegramFooterOnlyTextSuppressionPolicy.isRequested(
                outcome, ctaAction, messageObject, groupedMessages, currentChat, runtimeLinkPrefix);
    }

    /** Compatibility entry for callers that do not yet provide Telegram's runtime link host. */
    public static Result resolve(NoisePipelineOutcome outcome, NoiseAction existingPresentationAction,
                                 MessageObject messageObject, MessageObject.GroupedMessages groupedMessages,
                                 TLRPC.Chat currentChat) {
        return resolve(outcome, existingPresentationAction, messageObject, groupedMessages, currentChat, null);
    }
}
