package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Android View lifecycle coverage for the existing hidden-presentation primitive. */
@RunWith(AndroidJUnit4.class)
public final class TelegramFooterOnlyTextSuppressionCellLifecycleTest {

    @Test
    public void hiddenPresentationSuppressesMeasureDrawTouchAndAccessibilityThenRebindRestores() {
        ChatMessageCell cell = newCell();
        MessageObject footer = message("https://t.me/current");
        MessageObject ordinary = message("ordinary visible text");

        cell.setMessageObject(footer, null, false, false, false);
        cell.applyCleargramHiddenPresentation();
        cell.measure(exactly(720), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        cell.layout(0, 0, 720, cell.getMeasuredHeight());

        assertTrue(cell.isCleargramReplacementPresentationActive());
        assertEquals(0, cell.getMeasuredHeight());
        assertEquals(0, cell.getLayoutParams().height);
        assertDrawsNoPixels(cell);
        assertTrue(cell.onTouchEvent(event(MotionEvent.ACTION_DOWN)));
        assertTrue(cell.onTouchEvent(event(MotionEvent.ACTION_UP)));
        assertFalse(cell.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, new Bundle()));

        AccessibilityNodeProvider provider = cell.getAccessibilityNodeProvider();
        assertNull(provider.createAccessibilityNodeInfo(1));
        AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID);
        assertFalse(host.isClickable());
        assertFalse(host.isFocusable());
        host.recycle();

        cell.setMessageObject(ordinary, null, false, false, false);
        cell.measure(exactly(720), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));

        assertFalse(cell.isCleargramReplacementPresentationActive());
        assertEquals(240, cell.getLayoutParams().height);
        assertTrue(cell.getMeasuredHeight() >= 0);
    }

    @Test
    public void hiddenToMediaAndRepeatedHideDoNotLeaveStaleLayoutOverride() {
        ChatMessageCell cell = newCell();
        MessageObject first = message("https://t.me/current");
        MessageObject second = message("https://t.me/current-two");
        MessageObject media = mediaMessage();

        cell.setMessageObject(first, null, false, false, false);
        cell.applyCleargramHiddenPresentation();
        cell.setMessageObject(second, null, false, false, false);
        assertFalse(cell.isCleargramReplacementPresentationActive());
        assertEquals(240, cell.getLayoutParams().height);

        cell.applyCleargramHiddenPresentation();
        cell.setMessageObject(media, null, false, false, false);
        assertFalse(cell.isCleargramReplacementPresentationActive());
        assertEquals(240, cell.getLayoutParams().height);
    }

    @Test
    public void ordinaryToHiddenSameMessageAndResetDoNotLeaveStaleState() {
        ChatMessageCell cell = newCell();
        MessageObject ordinary = message("ordinary");
        MessageObject footer = message("https://t.me/current");

        cell.setMessageObject(ordinary, null, false, false, false);
        assertFalse(cell.isCleargramReplacementPresentationActive());
        cell.setMessageObject(footer, null, false, false, false);
        cell.applyCleargramHiddenPresentation();
        assertTrue(cell.isCleargramReplacementPresentationActive());
        cell.applyCleargramHiddenPresentation();
        assertTrue(cell.isCleargramReplacementPresentationActive());
        cell.resetCleargramVisualEffect();

        assertFalse(cell.isCleargramReplacementPresentationActive());
        assertEquals(240, cell.getLayoutParams().height);
        cell.setMessageObject(ordinary, null, false, false, false);
        assertFalse(cell.isCleargramReplacementPresentationActive());
    }

    private static ChatMessageCell newCell() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatMessageCell cell = new ChatMessageCell(context, 0);
        cell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 240));
        return cell;
    }

    private static int exactly(int width) {
        return View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
    }

    private static MotionEvent event(int action) {
        return MotionEvent.obtain(0L, 0L, action, 10f, 10f, 0);
    }

    private static void assertDrawsNoPixels(ChatMessageCell cell) {
        Bitmap bitmap = Bitmap.createBitmap(720, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cell.draw(canvas);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) != 0) {
                    throw new AssertionError("Hidden cell drew a pixel at " + x + "," + y);
                }
            }
        }
    }

    private static MessageObject message(String text) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = 1000;
        raw.message = text;
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        return new MessageObject(0, raw, false, false);
    }

    private static MessageObject mediaMessage() {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.id = 1001;
        raw.message = "caption";
        raw.media = new TLRPC.TL_messageMediaPhoto();
        raw.media.photo = new TLRPC.TL_photo();
        raw.peer_id = new TLRPC.TL_peerChannel();
        raw.peer_id.channel_id = 100;
        return new MessageObject(0, raw, false, false);
    }
}
