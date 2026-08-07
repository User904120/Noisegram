package org.telegram.ui.Cells;


import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoisePipelineOutcome;
import org.cleargram.integration.TelegramFooterOnlyTextBindIntegration;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Semantic HIDE selection lifecycle verification. */
@RunWith(AndroidJUnit4.class)
public final class ChatMessageCellHostInteractionStateLifecycleTest {

    @Test
    public void selectedMessageDoesNotRemainSelectedAfterSemanticCoreHide() {
        MessageObject message = urlMessage(3203, "https://t.me/current");
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            fixture.bind(message);
            assertTrue(fixture.snapshot().measuredHeight > 0);
            ChatMessageCellHostFixture.SelectionState before = fixture.selectRange(0, "https".length());
            assertTrue(before.selected);
            assertTrue(before.inSelectionMode);
            assertTrue(before.end > before.start);
            ChatMessageCellHostFixture.HideApplyResult hide = fixture.applySemanticHideAndCapture(message, message);
            ChatMessageCellHostFixture.Baseline hidden = fixture.snapshot();
            ChatMessageCellHostFixture.SelectionState after = hide.selection;
            assertTrue(hide.callbackCompleted);
            assertFalse(after.selected);
            assertFalse(after.inSelectionMode);
            assertEquals(-1, after.start);
            assertEquals(-1, after.end);
            assertEquals(0, hide.measuredHeight);
            assertEquals(0, hidden.measuredHeight);
            assertEquals(0, hidden.pixels);
        }
    }

    @Test
    public void selectedFooterDoesNotRemainSelectedAfterSameMessageHide() {
        MessageObject footer = urlMessage(3202, "https://t.me/current");
        TLRPC.MessageEntity entity = footer.messageOwner.entities.get(0);
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            CharSequence sourceText = footer.messageText;
            String sourceRawText = footer.messageOwner.message;
            int sourceOffset = entity.offset;
            int sourceLength = entity.length;
            fixture.bind(footer);
            ChatMessageCellHostFixture.SelectionState before = fixture.selectRange(0, "https".length());
            assertTrue(before.selected);
            TelegramFooterOnlyTextBindIntegration.Request request = TelegramFooterOnlyTextBindIntegration.prepare(
                    NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, null, footer, null, currentChannel(), "https://t.me/");
            assertTrue(request.isRequested());
            ChatMessageCellHostFixture.HideApplyResult hide = fixture.applyFooterRequestAndCapture(request, footer, footer);
            ChatMessageCellHostFixture.Baseline hidden = fixture.snapshot();
            ChatMessageCellHostFixture.SelectionState after = hide.selection;
            assertTrue(hide.callbackCompleted);
            assertFalse(after.selected);
            assertFalse(after.inSelectionMode);
            assertEquals(-1, after.start);
            assertEquals(-1, after.end);
            assertEquals(0, hide.measuredHeight);
            assertEquals(0, hidden.measuredHeight);
            assertEquals(0, hidden.pixels);
            assertEquals(sourceText, footer.messageText);
            assertEquals(sourceRawText, footer.messageOwner.message);
            assertSame(entity, footer.messageOwner.entities.get(0));
            assertEquals(sourceOffset, entity.offset);
            assertEquals(sourceLength, entity.length);
        }
    }

    private static TLRPC.TL_channel currentChannel() {
        TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = 100;
        channel.username = "current";
        channel.broadcast = true;
        return channel;
    }

    private static MessageObject urlMessage(int id, String text) {
        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
        entity.offset = 0;
        entity.length = text.length();
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = id;
        raw.date = 1;
        raw.message = text;
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        raw.entities.add(entity);
        return new MessageObject(0, raw, false, false);
    }
}


