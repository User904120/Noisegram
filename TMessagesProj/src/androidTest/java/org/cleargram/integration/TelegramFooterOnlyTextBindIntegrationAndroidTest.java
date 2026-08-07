package org.cleargram.integration;

import android.content.Context;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Android-runtime coverage for the production footer-only bind seam. */
@RunWith(AndroidJUnit4.class)
public final class TelegramFooterOnlyTextBindIntegrationAndroidTest {

    @Test
    public void urlFooterUsesProductionRequestBindAndApplyWithoutMutatingTelegramData() {
        String text = "https://t.me/current";
        MessageObject message = urlMessage(text, text, 0);
        TLRPC.MessageEntity entity = message.messageOwner.entities.get(0);
        ChatMessageCell cell = newCell();

        TelegramFooterOnlyTextBindIntegration.Request request = TelegramFooterOnlyTextBindIntegration.prepare(
                NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, null, message, null, currentChannel(), null);

        assertTrue(request.isRequested());
        cell.setMessageObject(message, null, false, false, false);
        assertSame(message, cell.getMessageObject());
        TelegramFooterOnlyTextBindIntegration.apply(request, cell,
                new TelegramDecisionContext(0, message, cell, null));

        assertTrue(cell.isCleargramReplacementPresentationActive());
        assertEquals(text, message.messageText.toString());
        assertEquals(text, message.messageOwner.message);
        assertEquals(0, entity.offset);
        assertEquals(text.length(), entity.length);
        assertSame(entity, message.messageOwner.entities.get(0));
    }

    @Test
    public void textUrlFooterUsesProductionRequestAndKeepsVisibleTextAndEntityIntact() {
        String visibleText = "open current channel";
        MessageObject message = textUrlMessage(visibleText, "https://t.me/current");
        TLRPC.TL_messageEntityTextUrl entity = (TLRPC.TL_messageEntityTextUrl) message.messageOwner.entities.get(0);
        ChatMessageCell cell = newCell();

        TelegramFooterOnlyTextBindIntegration.Request request = TelegramFooterOnlyTextBindIntegration.prepare(
                NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, null, message, null, currentChannel(), null);

        assertTrue(request.isRequested());
        cell.setMessageObject(message, null, false, false, false);
        TelegramFooterOnlyTextBindIntegration.apply(request, cell,
                new TelegramDecisionContext(0, message, cell, null));

        assertTrue(cell.isCleargramReplacementPresentationActive());
        assertEquals(visibleText, message.messageText.toString());
        assertEquals(visibleText, message.messageOwner.message);
        assertEquals("https://t.me/current", entity.url);
        assertEquals(0, entity.offset);
        assertEquals(visibleText.length(), entity.length);
    }

    @Test
    public void nonFooterShapesAndIdentityMismatchFailOpen() {
        MessageObject external = urlMessage("https://example.com", "https://example.com", 0);
        MessageObject retainedPrefix = urlMessage("body\nhttps://t.me/current", "https://t.me/current", 5);
        MessageObject grouped = urlMessage("https://t.me/current", "https://t.me/current", 0);
        MessageObject first = urlMessage("https://t.me/current", "https://t.me/current", 0);
        MessageObject second = message("ordinary", new TLRPC.TL_messageEntityUrl(), 1001);
        ChatMessageCell cell = newCell();

        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, external, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, retainedPrefix, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, grouped, new MessageObject.GroupedMessages(), currentChannel(), null).isRequested());

        TelegramFooterOnlyTextBindIntegration.Request requested = TelegramFooterOnlyTextBindIntegration.prepare(
                NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO, null, first, null, currentChannel(), null);
        assertTrue(requested.isRequested());
        cell.setMessageObject(second, null, false, false, false);
        TelegramFooterOnlyTextBindIntegration.apply(requested, cell,
                new TelegramDecisionContext(0, first, cell, null));

        assertFalse(cell.isCleargramReplacementPresentationActive());
        assertSame(second, cell.getMessageObject());
        assertNotNull(second.messageText);
    }

    @Test
    public void terminalAndIneligibleTelegramShapesKeepNormalPresentation() {
        MessageObject candidate = urlMessage("https://t.me/current", "https://t.me/current", 0);
        MessageObject sponsored = urlMessage("https://t.me/current", "https://t.me/current", 0);
        sponsored.sponsoredId = new byte[] {1};
        MessageObject caption = urlMessage("https://t.me/current", "https://t.me/current", 0);
        caption.caption = caption.messageOwner.message;
        MessageObject media = urlMessage("https://t.me/current", "https://t.me/current", 0);
        media.messageOwner.media = new TLRPC.TL_messageMediaWebPage();
        TLRPC.TL_chat group = new TLRPC.TL_chat();
        group.id = 100;

        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.TERMINAL_DECISION,
                null, candidate, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                NoiseAction.HIDE, candidate, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                NoiseAction.COLLAPSE, candidate, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, candidate, null, group, null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, sponsored, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, caption, null, currentChannel(), null).isRequested());
        assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, media, null, currentChannel(), null).isRequested());
    }

    private static ChatMessageCell newCell() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatMessageCell cell = new ChatMessageCell(context, 0);
        cell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 240));
        return cell;
    }

    private static TLRPC.TL_channel currentChannel() {
        TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = 100;
        channel.username = "current";
        channel.broadcast = true;
        return channel;
    }

    private static MessageObject urlMessage(String fullText, String url, int offset) {
        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
        entity.offset = offset;
        entity.length = url.length();
        return message(fullText, entity);
    }

    private static MessageObject textUrlMessage(String visibleText, String url) {
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = 0;
        entity.length = visibleText.length();
        entity.url = url;
        return message(visibleText, entity);
    }

    private static MessageObject message(String text, TLRPC.MessageEntity entity) {
        return message(text, entity, 1000);
    }

    private static MessageObject message(String text, TLRPC.MessageEntity entity, int id) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = id;
        raw.message = text;
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        raw.entities.add(entity);
        return new MessageObject(0, raw, false, false);
    }
}
