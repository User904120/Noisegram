package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Decides whether a current bind may use the existing full-cell hidden presentation for a footer-only text message. */
final class TelegramFooterOnlyTextSuppressionPolicy {

    interface FooterClassifier {
        TelegramChannelFooterLinkClassifier.TrimResult classify(
                String text,
                java.util.List<TLRPC.MessageEntity> entities,
                TLRPC.Chat currentChat,
                String runtimeLinkPrefix
        );
    }

    private TelegramFooterOnlyTextSuppressionPolicy() {
    }

    static boolean isMediaFree(TLRPC.MessageMedia media) {
        return media == null || media instanceof TLRPC.TL_messageMediaEmpty;
    }

    static boolean isRequested(
            NoisePipelineOutcome outcome,
            NoiseAction ctaAction,
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix
    ) {
        return isRequested(outcome, ctaAction, Candidate.from(messageObject), groupedMessages != null,
                currentChat, runtimeLinkPrefix, TelegramChannelFooterLinkClassifier::classifyLastLine);
    }

    static boolean isRequested(
            NoisePipelineOutcome outcome,
            NoiseAction ctaAction,
            Candidate candidate,
            boolean grouped,
            TLRPC.Chat currentChat,
            String runtimeLinkPrefix,
            FooterClassifier classifier
    ) {
        try {
            if (outcome != NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO
                    || ctaAction != null
                    || candidate == null
                    || grouped
                    || currentChat == null
                    || classifier == null
                    || !candidate.isEligibleTextOnly()) {
                return false;
            }
            TelegramChannelFooterLinkClassifier.TrimResult result = classifier.classify(
                    candidate.text,
                    candidate.entities,
                    currentChat,
                    runtimeLinkPrefix
            );
            return result != null && result.isMatch() && result.startInclusive == 0;
        } catch (RuntimeException ignored) {
            // Fail-open: leave Telegram's normal text presentation intact.
            return false;
        }
    }

    static final class Candidate {
        final String text;
        final java.util.List<TLRPC.MessageEntity> entities;
        final boolean textOnly, mediaAbsent, captionAbsent, sponsored, service, action, replyMarkup, forwarded, reply;

        Candidate(String text, java.util.List<TLRPC.MessageEntity> entities, boolean textOnly, boolean mediaAbsent,
                  boolean captionAbsent, boolean sponsored, boolean service, boolean action, boolean replyMarkup,
                  boolean forwarded, boolean reply) {
            this.text = text; this.entities = entities; this.textOnly = textOnly; this.mediaAbsent = mediaAbsent;
            this.captionAbsent = captionAbsent; this.sponsored = sponsored; this.service = service; this.action = action;
            this.replyMarkup = replyMarkup; this.forwarded = forwarded; this.reply = reply;
        }

        static Candidate from(MessageObject messageObject) {
            if (messageObject == null || messageObject.messageOwner == null) return null;
            TLRPC.Message owner = messageObject.messageOwner;
            return fromRawTelegramShape(owner.message, owner.entities, messageObject.type, messageObject.contentType,
                    owner.media, messageObject.caption, messageObject.isSponsored(),
                    owner instanceof TLRPC.TL_messageService, owner.action, owner.reply_markup, owner.fwd_from, owner.reply_to);
        }

        static Candidate fromRawTelegramShape(String text, java.util.List<TLRPC.MessageEntity> entities,
                                               int messageType, int contentType, TLRPC.MessageMedia media,
                                               CharSequence caption, boolean sponsored, boolean service,
                                               TLRPC.MessageAction action, TLRPC.ReplyMarkup replyMarkup,
                                               TLRPC.MessageFwdHeader forward, TLRPC.MessageReplyHeader reply) {
            return new Candidate(text, entities, messageType == MessageObject.TYPE_TEXT && contentType == 0,
                    isMediaFree(media), caption == null, sponsored, service, action != null, replyMarkup != null,
                    forward != null, reply != null);
        }

        boolean isEligibleTextOnly() {
            return textOnly && mediaAbsent && captionAbsent && !sponsored && !service && !action
                    && !replyMarkup && !forwarded && !reply && text != null;
        }
    }
}
