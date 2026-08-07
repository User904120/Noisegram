package org.telegram.ui.Cells;

import android.text.Spanned;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.cleargram.api.NoisePipelineOutcome;
import org.cleargram.integration.TelegramFooterOnlyTextBindIntegration;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Attached Android lifecycle coverage for the production footer-only suppression seam. */
@RunWith(AndroidJUnit4.class)
public final class ChatMessageCellHostFooterSuppressionLifecycleTest {

    private static final String RUNTIME_LINK_PREFIX = "https://t.me/";

    @Test
    public void urlAndTextUrlFooterFollowProductionLifecycleWithoutStaleHiddenState() {
        MessageObject ordinary = urlMessage(3001, "https://example.com");
        MessageObject footerUrl = urlMessage(3002, "https://t.me/current");
        MessageObject footerUrlSecond = urlMessage(3003, "https://telegram.me/current");
        MessageObject footerTextUrl = textUrlMessage(3004, "open current channel", "https://t.me/current");
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            fixture.bind(ordinary);
            ChatMessageCellHostFixture.Baseline ordinaryBaseline = fixture.snapshot();
            assertOrdinary(ordinaryBaseline, ordinary, ordinary.messageOwner.message);
            assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
            ChatMessageCellHostFixture.TouchPoint ordinaryPoint = fixture.lastUrlTouchPoint();
            assertNotNull(ordinaryPoint);
            assertEquals(1, fixture.urlCallbackCount());

            applyAndAssertHidden(fixture, footerUrl);
            int callbacksAfterUrlHidden = fixture.urlCallbackCount();
            assertFalse(fixture.dispatchTouchAt(ordinaryPoint));
            assertEquals(callbacksAfterUrlHidden, fixture.urlCallbackCount());

            fixture.bind(ordinary);
            assertRestored(fixture, ordinary, ordinaryBaseline, ordinaryPoint);

            applyAndAssertHidden(fixture, footerTextUrl);
            int callbacksAfterTextUrlHidden = fixture.urlCallbackCount();
            assertFalse(fixture.dispatchTouchAt(ordinaryPoint));
            assertEquals(callbacksAfterTextUrlHidden, fixture.urlCallbackCount());

            fixture.bind(ordinary);
            assertRestored(fixture, ordinary, ordinaryBaseline, ordinaryPoint);

            applyAndAssertHidden(fixture, footerUrl);
            applyAndAssertHidden(fixture, footerUrl);
            applyAndAssertHidden(fixture, footerUrlSecond);
            applyAndAssertHidden(fixture, footerTextUrl);
            applyAndAssertHidden(fixture, footerUrl);

            fixture.removeAndAdd();
            fixture.bind(ordinary);
            assertRestored(fixture, ordinary, ordinaryBaseline, ordinaryPoint);

            applyAndAssertHidden(fixture, footerUrl);
            fixture.recreateAndBind(ordinary);
            ChatMessageCellHostFixture.Baseline recreated = fixture.snapshot();
            assertOrdinary(recreated, ordinary, ordinary.messageOwner.message);
            assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
            assertTrue(fixture.urlCallbackCount() > 0);
        }
    }

    @Test
    public void failOpenRequestsAndIdentityMismatchKeepAttachedOrdinaryPresentation() {
        MessageObject ordinary = urlMessage(3101, "https://example.com");
        MessageObject requestedFooter = urlMessage(3102, "https://t.me/current");
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            assertFalse(request(urlMessage(3103, "https://example.com")).isRequested());
            assertFalse(request(urlMessage(3104, "https://t.me/other")).isRequested());
            assertFalse(request(urlMessage(3105, "body\nhttps://t.me/current")).isRequested());
            assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                    null, requestedFooter, new MessageObject.GroupedMessages(), currentChannel(), null).isRequested());
            assertFalse(TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                    null, requestedFooter, null, nonBroadcastChannel(), null).isRequested());
            MessageObject sponsored = urlMessage(3106, "https://t.me/current");
            sponsored.sponsoredId = new byte[] {1};
            assertFalse(request(sponsored).isRequested());
            MessageObject webPage = urlMessage(3107, "https://t.me/current");
            webPage.messageOwner.media = new TLRPC.TL_messageMediaWebPage();
            assertFalse(request(webPage).isRequested());
            MessageObject photo = urlMessage(3109, "https://t.me/current");
            photo.messageOwner.media = new TLRPC.TL_messageMediaPhoto();
            photo.messageOwner.media.photo = new TLRPC.TL_photo();
            assertFalse(request(photo).isRequested());
            MessageObject document = urlMessage(3110, "https://t.me/current");
            document.messageOwner.media = new TLRPC.TL_messageMediaDocument();
            document.messageOwner.media.document = new TLRPC.TL_document();
            assertFalse(request(document).isRequested());
            assertFalse(request(serviceMessage(3111, "https://t.me/current")).isRequested());

            fixture.bind(ordinary);
            ChatMessageCellHostFixture.Baseline baseline = fixture.snapshot();
            assertOrdinary(baseline, ordinary, ordinary.messageOwner.message);
            TelegramFooterOnlyTextBindIntegration.Request requested = request(requestedFooter);
            assertTrue(requested.isRequested());
            fixture.applyFooterRequest(requested, requestedFooter, requestedFooter);
            assertOrdinary(fixture.snapshot(), ordinary, ordinary.messageOwner.message);
            assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
            assertEquals(1, fixture.urlCallbackCount());

            MessageObject retainedPrefix = urlMessage(3108, "body\nhttps://t.me/current");
            fixture.bind(retainedPrefix);
            fixture.applyFooterRequest(request(retainedPrefix), retainedPrefix, retainedPrefix);
            assertOrdinary(fixture.snapshot(), retainedPrefix, retainedPrefix.messageOwner.message);
        }
    }

    private static void applyAndAssertHidden(ChatMessageCellHostFixture fixture, MessageObject footer) {
        TelegramFooterOnlyTextBindIntegration.Request request = request(footer);
        assertTrue(request.isRequested());
        fixture.bind(footer);
        ChatMessageCellHostFixture.Baseline before = fixture.snapshot();
        assertOrdinary(before, footer, footer.messageOwner.message);
        Log.i("CleargramFooterLifecycle", "ordinary id=" + footer.getId()
                + " width=" + before.measuredWidth + " height=" + before.measuredHeight
                + " layoutHeight=" + before.layoutHeight + " layoutParam=" + before.layoutParamHeight
                + " pixels=" + before.pixels);
        assertTrue(footer.messageText instanceof Spanned);
        assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
        int callbacksBeforeHidden = fixture.urlCallbackCount();
        ChatMessageCellHostFixture.TouchPoint point = fixture.lastUrlTouchPoint();
        SourceState source = SourceState.capture(footer);
        fixture.applyFooterRequest(request, footer, footer);
        ChatMessageCellHostFixture.Baseline hidden = fixture.snapshot();
        assertSame(footer, hidden.messageObject);
        assertEquals(0, hidden.measuredHeight);
        assertEquals(0, hidden.layoutHeight);
        assertEquals(0, hidden.layoutParamHeight);
        assertEquals(0, hidden.pixels);
        Log.i("CleargramFooterLifecycle", "hidden id=" + footer.getId()
                + " width=" + hidden.measuredWidth + " height=" + hidden.measuredHeight
                + " layoutHeight=" + hidden.layoutHeight + " layoutParam=" + hidden.layoutParamHeight
                + " pixels=" + hidden.pixels);
        assertFalse(accessible(hidden).contains(footer.messageOwner.message));
        assertFalse(fixture.dispatchTouchAt(point));
        assertEquals(callbacksBeforeHidden, fixture.urlCallbackCount());
        source.assertUnchanged(footer);
    }

    private static void assertRestored(ChatMessageCellHostFixture fixture, MessageObject ordinary,
                                       ChatMessageCellHostFixture.Baseline baseline,
                                       ChatMessageCellHostFixture.TouchPoint point) {
        ChatMessageCellHostFixture.Baseline restored = fixture.snapshot();
        assertOrdinary(restored, ordinary, ordinary.messageOwner.message);
        assertEquals(baseline.measuredHeight, restored.measuredHeight);
        int callbacksBeforeRestore = fixture.urlCallbackCount();
        assertTrue(fixture.dispatchTouchAt(point));
        assertEquals(callbacksBeforeRestore + 1, fixture.urlCallbackCount());
    }

    private static void assertOrdinary(ChatMessageCellHostFixture.Baseline baseline, MessageObject expected,
                                       String text) {
        assertSame(expected, baseline.messageObject);
        assertTrue(baseline.measuredWidth > 0);
        assertTrue(baseline.measuredHeight > 0);
        assertTrue(baseline.layoutHeight > 0);
        assertTrue(baseline.layoutParamHeight != 0);
        assertTrue(baseline.hasTextLayout);
        assertTrue(baseline.pixels > 0);
        assertTrue(accessible(baseline).contains(text));
    }

    private static String accessible(ChatMessageCellHostFixture.Baseline baseline) {
        return String.valueOf(baseline.accessibilityText) + " " + String.valueOf(baseline.accessibilityDescription);
    }

    private static TelegramFooterOnlyTextBindIntegration.Request request(MessageObject message) {
        return TelegramFooterOnlyTextBindIntegration.prepare(NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO,
                null, message, null, currentChannel(), RUNTIME_LINK_PREFIX);
    }

    private static TLRPC.TL_channel currentChannel() {
        TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = 100;
        channel.username = "current";
        channel.broadcast = true;
        return channel;
    }

    private static TLRPC.TL_chat nonBroadcastChannel() {
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = 100;
        return chat;
    }

    private static MessageObject urlMessage(int id, String text) {
        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
        entity.offset = text.lastIndexOf("https://");
        entity.length = text.length() - entity.offset;
        return message(id, text, entity);
    }

    private static MessageObject textUrlMessage(int id, String visibleText, String url) {
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = 0;
        entity.length = visibleText.length();
        entity.url = url;
        return message(id, visibleText, entity);
    }

    private static MessageObject message(int id, String text, TLRPC.MessageEntity entity) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = id;
        raw.date = 1;
        raw.message = text;
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        raw.entities.add(entity);
        return new MessageObject(0, raw, false, false);
    }

    private static MessageObject serviceMessage(int id, String text) {
        TLRPC.TL_messageService raw = new TLRPC.TL_messageService();
        raw.id = id;
        raw.date = 1;
        raw.message = text;
        raw.action = new TLRPC.TL_messageActionEmpty();
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        return new MessageObject(0, raw, false, false);
    }

    private static final class SourceState {
        private final CharSequence messageText;
        private final String rawText;
        private final ArrayList<TLRPC.MessageEntity> entities;
        private final TLRPC.MessageEntity entity;
        private final int offset;
        private final int length;
        private final String url;
        private final ArrayList<MessageObject.TextLayoutBlock> layouts;

        private SourceState(MessageObject message) {
            messageText = message.messageText;
            rawText = message.messageOwner.message;
            entities = message.messageOwner.entities;
            entity = entities.get(0);
            offset = entity.offset;
            length = entity.length;
            url = entity instanceof TLRPC.TL_messageEntityTextUrl
                    ? ((TLRPC.TL_messageEntityTextUrl) entity).url : null;
            layouts = message.textLayoutBlocks;
        }

        static SourceState capture(MessageObject message) {
            return new SourceState(message);
        }

        void assertUnchanged(MessageObject message) {
            assertSame(messageText, message.messageText);
            assertEquals(rawText, message.messageOwner.message);
            assertSame(entities, message.messageOwner.entities);
            assertSame(entity, message.messageOwner.entities.get(0));
            assertEquals(offset, entity.offset);
            assertEquals(length, entity.length);
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                assertEquals(url, ((TLRPC.TL_messageEntityTextUrl) entity).url);
            }
            assertSame(layouts, message.textLayoutBlocks);
        }
    }
}
