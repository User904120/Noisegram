package org.cleargram.integration;

import org.telegram.tgnet.TLRPC;

/** Immutable data required by the CTA decision boundary. */
final class MessageCtaClassificationInput {

    final TLRPC.ReplyMarkup replyMarkup;
    final boolean sponsored;
    final boolean grouped;

    MessageCtaClassificationInput(
            TLRPC.ReplyMarkup replyMarkup,
            boolean sponsored,
            boolean grouped
    ) {
        this.replyMarkup = replyMarkup;
        this.sponsored = sponsored;
        this.grouped = grouped;
    }
}
