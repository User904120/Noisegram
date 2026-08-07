package org.cleargram.integration;

import org.junit.Test;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramMessageCtaClassifierTest {

    @Test
    public void singleUrlButtonMatches() {
        assertTrue(TelegramMessageCtaClassifier.hasTargetCtaButton(
                inputWith(singleRow(new TLRPC.TL_keyboardButtonUrl()))));
    }

    @Test
    public void multipleUrlButtonsInOneRowMatch() {
        assertTrue(TelegramMessageCtaClassifier.hasTargetCtaButton(
                inputWith(rowOf(new TLRPC.TL_keyboardButtonUrl(), new TLRPC.TL_keyboardButtonUrl()))));
    }

    @Test
    public void severalUrlButtonsInOneRowMatch() {
        assertTrue(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(rowOf(
                new TLRPC.TL_keyboardButtonUrl(), new TLRPC.TL_keyboardButtonUrl(),
                new TLRPC.TL_keyboardButtonUrl()))));
    }

    @Test
    public void twoRowsWithOneUrlButtonEachMatch() {
        TLRPC.TL_replyInlineMarkup markup = rowOf(new TLRPC.TL_keyboardButtonUrl());
        markup.rows.add(buttonRow(new TLRPC.TL_keyboardButtonUrl()));
        assertTrue(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(markup)));
    }

    @Test
    public void multipleRowsWithDifferentUrlButtonCountsMatch() {
        TLRPC.TL_replyInlineMarkup markup = rowOf(new TLRPC.TL_keyboardButtonUrl());
        markup.rows.add(buttonRow(new TLRPC.TL_keyboardButtonUrl(),
                new TLRPC.TL_keyboardButtonUrl(), new TLRPC.TL_keyboardButtonUrl()));
        assertTrue(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(markup)));
    }

    @Test
    public void nullSponsoredAndGroupedMessagesFailOpen() {
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(null));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(
                new MessageCtaClassificationInput(singleRow(new TLRPC.TL_keyboardButtonUrl()), true, false)));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(
                new MessageCtaClassificationInput(singleRow(new TLRPC.TL_keyboardButtonUrl()), false, true)));
    }

    @Test
    public void unsupportedMarkupShapesFailOpen() {
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(null)));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(new TLRPC.TL_replyKeyboardMarkup())));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(new TLRPC.TL_replyInlineMarkup())));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(singleRow(new TLRPC.TL_keyboardButtonCallback()))));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(singleRow(new TLRPC.TL_keyboardButtonUrlAuth()))));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(singleRow(new TLRPC.TL_keyboardButtonWebView()))));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(singleRow(new TLRPC.TL_keyboardButtonSimpleWebView()))));

        TLRPC.TL_replyInlineMarkup emptyRow = singleRow(new TLRPC.TL_keyboardButtonUrl());
        emptyRow.rows.add(new TLRPC.TL_keyboardButtonRow());
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(emptyRow)));
        TLRPC.TL_replyInlineMarkup nullRow = singleRow(new TLRPC.TL_keyboardButtonUrl());
        nullRow.rows.add(null);
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(nullRow)));
        TLRPC.TL_replyInlineMarkup nullButton = singleRow(new TLRPC.TL_keyboardButtonUrl());
        nullButton.rows.get(0).buttons.add(null);
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(nullButton)));
        TLRPC.TL_replyInlineMarkup mixedRows = singleRow(new TLRPC.TL_keyboardButtonUrl());
        mixedRows.rows.add(buttonRow(new TLRPC.TL_keyboardButtonCallback()));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(mixedRows)));
        assertFalse(TelegramMessageCtaClassifier.hasTargetCtaButton(inputWith(rowOf(
                new TLRPC.TL_keyboardButtonUrl(), new TLRPC.TL_keyboardButtonCallback()))));
    }

    private static MessageCtaClassificationInput inputWith(TLRPC.ReplyMarkup replyMarkup) {
        return new MessageCtaClassificationInput(replyMarkup, false, false);
    }

    private static TLRPC.TL_replyInlineMarkup singleRow(TLRPC.KeyboardButton button) {
        return rowOf(button);
    }

    private static TLRPC.TL_replyInlineMarkup rowOf(TLRPC.KeyboardButton... buttons) {
        TLRPC.TL_replyInlineMarkup markup = new TLRPC.TL_replyInlineMarkup();
        markup.rows.add(buttonRow(buttons));
        return markup;
    }

    private static TLRPC.TL_keyboardButtonRow buttonRow(TLRPC.KeyboardButton... buttons) {
        TLRPC.TL_keyboardButtonRow row = new TLRPC.TL_keyboardButtonRow();
        for (TLRPC.KeyboardButton button : buttons) {
            row.buttons.add(button);
        }
        return row;
    }
}
