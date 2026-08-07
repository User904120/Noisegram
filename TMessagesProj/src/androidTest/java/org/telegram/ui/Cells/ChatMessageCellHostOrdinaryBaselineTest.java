package org.telegram.ui.Cells;

import android.text.Spanned;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies an attached ordinary ChatMessageCell baseline without footer presentation. */
@RunWith(AndroidJUnit4.class)
public final class ChatMessageCellHostOrdinaryBaselineTest {

    @Test
    public void ordinaryBindDrawAccessibilitySelectionAndRebindUseAttachedProductionPath() {
        MessageObject first = ordinaryMessage(2001, "ordinary attached message");
        MessageObject second = ordinaryMessage(2002, "ordinary attached rebind");
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            fixture.bind(first);
            ChatMessageCellHostFixture.Baseline firstBaseline = fixture.snapshot();
            Log.i("CleargramHostBaseline", "width=" + firstBaseline.measuredWidth
                    + " measuredHeight=" + firstBaseline.measuredHeight
                    + " layoutHeight=" + firstBaseline.layoutHeight
                    + " layoutParamHeight=" + firstBaseline.layoutParamHeight
                    + " pixels=" + firstBaseline.pixels);
            assertOrdinaryBaseline(firstBaseline, first, "ordinary attached message");
            assertTrue(fixture.bootstrapSelectionHelper());

            fixture.bind(first);
            assertOrdinaryBaseline(fixture.snapshot(), first, "ordinary attached message");
            fixture.bind(second);
            assertOrdinaryBaseline(fixture.snapshot(), second, "ordinary attached rebind");
            fixture.removeAndAdd();
            fixture.bind(second);
            assertOrdinaryBaseline(fixture.snapshot(), second, "ordinary attached rebind");
            fixture.recreateAndBind(second);
            assertOrdinaryBaseline(fixture.snapshot(), second, "ordinary attached rebind");
        }
    }

    @Test
    public void ordinaryUrlDispatchesDelegateThroughRealTouchPathAfterRebindAndReattach() {
        MessageObject url = urlMessage(2101, "https://example.com");
        try (ChatMessageCellHostFixture fixture = ChatMessageCellHostFixture.launch()) {
            fixture.bind(url);
            ChatMessageCellHostFixture.Baseline baseline = fixture.snapshot();
            assertTrue(baseline.hasTextLayout);
            assertTrue(url.messageText instanceof Spanned);
            assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
            assertEquals(1, fixture.urlCallbackCount());
            assertNotNull(fixture.lastUrl());
            assertNotNull(fixture.lastUrlTouchPoint());

            fixture.removeAndAdd();
            fixture.bind(url);
            assertTrue(fixture.dispatchTouchGridUntilUrlCallback());
            assertEquals(2, fixture.urlCallbackCount());
        }
    }

    private static void assertOrdinaryBaseline(ChatMessageCellHostFixture.Baseline baseline,
                                               MessageObject expected, String text) {
        assertSame(expected, baseline.messageObject);
        assertTrue(baseline.measuredWidth > 0);
        assertTrue(baseline.measuredHeight > 0);
        assertTrue(baseline.layoutHeight > 0);
        assertTrue(baseline.layoutParamHeight != 0);
        assertTrue(baseline.hasTextLayout);
        assertTrue(baseline.pixels > 0);
        String accessible = String.valueOf(baseline.accessibilityText) + " "
                + String.valueOf(baseline.accessibilityDescription);
        assertTrue(accessible.contains(text));
        assertFalse(expected.isOutOwner());
    }

    private static MessageObject ordinaryMessage(int id, String text) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = id;
        raw.date = 1;
        raw.message = text;
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        return new MessageObject(0, raw, false, false);
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
