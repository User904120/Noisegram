package org.cleargram.integration;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;

/** Ends Telegram text selection only when the semantic HIDE target is selected. */
final class TelegramHideSelectionLifecycle {

    private TelegramHideSelectionLifecycle() {
    }

    static void clearCurrentMessageSelection(ChatMessageCell cell, MessageObject currentMessageObject) {
        if (cell == null || currentMessageObject == null || cell.getDelegate() == null) {
            return;
        }
        TextSelectionHelper.ChatListTextSelectionHelper helper = cell.getDelegate().getTextSelectionHelper();
        if (helper != null && helper.isSelected(currentMessageObject)) {
            helper.clear();
        }
    }
}
