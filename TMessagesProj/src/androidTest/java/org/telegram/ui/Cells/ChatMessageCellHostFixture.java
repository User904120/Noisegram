package org.telegram.ui.Cells;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.style.CharacterStyle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.cleargram.integration.TelegramDecisionContext;
import org.cleargram.integration.TelegramCollapseState;
import org.cleargram.integration.TelegramDecisionApplier;
import org.cleargram.integration.TelegramFooterOnlyTextBindIntegration;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Small AndroidTest-only fixture; it deliberately uses ChatMessageCell's ordinary bind path. */
final class ChatMessageCellHostFixture implements AutoCloseable {

    static final int ROOT_WIDTH_DP = 360;

    private final ActivityScenario<CleargramChatMessageCellHostActivity> scenario;
    private final AtomicInteger urlCallbackCount = new AtomicInteger();
    private final AtomicReference<CharacterStyle> lastUrl = new AtomicReference<>();
    private final AtomicReference<TouchPoint> lastUrlTouchPoint = new AtomicReference<>();
    private ChatMessageCell cell;
    private TextSelectionHelper.ChatListTextSelectionHelper selectionHelper;
    private CleargramChatMessageCellHostActivity host;

    private ChatMessageCellHostFixture(ActivityScenario<CleargramChatMessageCellHostActivity> scenario) {
        this.scenario = scenario;
        onActivity(this::createCell);
    }

    static ChatMessageCellHostFixture launch() {
        Log.d("CleargramSelectionTest", "fixture launch started");
        return new ChatMessageCellHostFixture(ActivityScenario.launch(CleargramChatMessageCellHostActivity.class));
    }

    void bind(MessageObject messageObject) {
        Log.d("CleargramSelectionTest", "bind started");
        onActivity(activity -> {
            cell.setMessageObject(messageObject, null, false, false, false);
            layoutCell(activity);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Log.d("CleargramSelectionTest", "bind completed");
    }

    void removeAndAdd() {
        onActivity(activity -> {
            activity.getRoot().removeView(cell);
            activity.getRoot().addView(cell, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            layoutCell(activity);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    void recreateAndBind(MessageObject messageObject) {
        scenario.recreate();
        onActivity(this::createCell);
        bind(messageObject);
    }

    void applyFooterRequest(TelegramFooterOnlyTextBindIntegration.Request request, MessageObject decisionMessage,
                            MessageObject boundMessage) {
        onActivity(activity -> {
            TelegramFooterOnlyTextBindIntegration.apply(request, cell,
                    new TelegramDecisionContext(0, decisionMessage, boundMessage, cell,
                            TelegramDecisionContext.PresentationRole.SINGLE, null));
        });
        Log.d("CleargramSelectionTest", "footer apply completed");
    }

    void applySemanticHide(MessageObject decisionMessage, MessageObject boundMessage) {
        onActivity(activity -> {
            new TelegramDecisionApplier().apply(new NoiseDecision(NoiseAction.HIDE),
                    new TelegramDecisionContext(0, decisionMessage, boundMessage, cell,
                            TelegramDecisionContext.PresentationRole.SINGLE, null), new TelegramCollapseState());
        });
        Log.d("CleargramSelectionTest", "core HIDE apply completed");
    }

    HideApplyResult applyFooterRequestAndCapture(TelegramFooterOnlyTextBindIntegration.Request request,
                                                 MessageObject decisionMessage, MessageObject boundMessage) {
        AtomicReference<HideApplyResult> result = new AtomicReference<>();
        onActivity(activity -> {
            TelegramFooterOnlyTextBindIntegration.apply(request, cell,
                    new TelegramDecisionContext(0, decisionMessage, boundMessage, cell,
                            TelegramDecisionContext.PresentationRole.SINGLE, null));
            result.set(new HideApplyResult(selectionSnapshot(boundMessage), cell.getMeasuredHeight(), true));
        });
        return result.get();
    }

    HideApplyResult applySemanticHideAndCapture(MessageObject decisionMessage, MessageObject boundMessage) {
        AtomicReference<HideApplyResult> result = new AtomicReference<>();
        onActivity(activity -> {
            new TelegramDecisionApplier().apply(new NoiseDecision(NoiseAction.HIDE),
                    new TelegramDecisionContext(0, decisionMessage, boundMessage, cell,
                            TelegramDecisionContext.PresentationRole.SINGLE, null), new TelegramCollapseState());
            result.set(new HideApplyResult(selectionSnapshot(boundMessage), cell.getMeasuredHeight(), true));
        });
        return result.get();
    }

    Baseline snapshot() {
        AtomicReference<Baseline> result = new AtomicReference<>();
        onActivity(activity -> {
            layoutCell(activity);
            AccessibilityNodeProvider provider = cell.getAccessibilityNodeProvider();
            AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID);
            CharSequence accessibilityText = host.getText();
            CharSequence accessibilityDescription = host.getContentDescription();
            boolean clickable = host.isClickable();
            boolean focusable = host.isFocusable();
            host.recycle();
            result.set(new Baseline(cell.getMeasuredWidth(), cell.getMeasuredHeight(), cell.getHeight(),
                    cell.getLayoutParams().height, cell.getMessageObject(),
                    cell.getMessageObject() != null && cell.getMessageObject().textLayoutBlocks != null
                            && !cell.getMessageObject().textLayoutBlocks.isEmpty(),
                    accessibilityText, accessibilityDescription, clickable, focusable, drawPixels(cell)));
        });
        return result.get();
    }

    boolean dispatchTouchGridUntilUrlCallback() {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        onActivity(activity -> {
            layoutCell(activity);
            int before = urlCallbackCount.get();
            long now = SystemClock.uptimeMillis();
            for (int y = 1; y < cell.getHeight() && urlCallbackCount.get() == before; y += 4) {
                for (int x = 1; x < cell.getWidth() && urlCallbackCount.get() == before; x += 4) {
                    dispatchTouchPair(x, y, now);
                    if (urlCallbackCount.get() == before + 1) {
                        lastUrlTouchPoint.set(new TouchPoint(x, y));
                    }
                }
            }
            result.set(urlCallbackCount.get() == before + 1);
        });
        return result.get();
    }

    boolean dispatchTouchAt(TouchPoint point) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        onActivity(activity -> {
            int before = urlCallbackCount.get();
            dispatchTouchPair(point.x, point.y, SystemClock.uptimeMillis());
            result.set(urlCallbackCount.get() == before + 1);
        });
        return result.get();
    }

    int urlCallbackCount() {
        return urlCallbackCount.get();
    }

    CharacterStyle lastUrl() {
        return lastUrl.get();
    }

    TouchPoint lastUrlTouchPoint() {
        return lastUrlTouchPoint.get();
    }

    boolean bootstrapSelectionHelper() {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        onActivity(activity -> {
            TextSelectionHelper.ChatListTextSelectionHelper helper = new TextSelectionHelper.ChatListTextSelectionHelper();
            helper.setParentView(activity.getRoot());
            helper.setMaybeTextCord(0, 0);
            helper.setMessageObject(cell);
            result.set(cell.getMessageObject() != null && cell.getMessageObject().textLayoutBlocks != null);
            helper.clear();
        });
        return result.get();
    }

    SelectionState selectRange(int start, int end) {
        AtomicReference<SelectionState> result = new AtomicReference<>();
        onActivity(activity -> {
            selectionHelper.select(cell, start, end);
            result.set(selectionSnapshot(cell.getMessageObject()));
        });
        return result.get();
    }

    void clearSelection() {
        onActivity(activity -> selectionHelper.clear());
    }

    SelectionState selectionState(MessageObject messageObject) {
        AtomicReference<SelectionState> result = new AtomicReference<>();
        onActivity(activity -> result.set(selectionSnapshot(messageObject)));
        return result.get();
    }

    private void onActivity(ActivityScenario.ActivityAction<CleargramChatMessageCellHostActivity> action) {
        scenario.onActivity(action);
    }

    private void createCell(CleargramChatMessageCellHostActivity activity) {
        host = activity;
        selectionHelper = new TextSelectionHelper.ChatListTextSelectionHelper();
        selectionHelper.setParentView(activity.getRoot());
        View overlay = selectionHelper.getOverlayView(activity);
        activity.getRoot().addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Log.d("CleargramSelectionTest", "cell/overlay attached");
        cell = new ChatMessageCell(activity, 0);
        cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return true;
            }

            @Override
            public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                return selectionHelper;
            }

            @Override
            public void didPressUrl(ChatMessageCell pressedCell, CharacterStyle url, boolean longPress) {
                if (!longPress) {
                    lastUrl.set(url);
                    urlCallbackCount.incrementAndGet();
                }
            }

            @Override
            public void didLongPress(ChatMessageCell pressedCell, float x, float y) {
                // Declared only to establish the ordinary delegate capability; this stage does not time long press.
            }
        });
        activity.getRoot().addView(cell, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private SelectionState selectionSnapshot(MessageObject messageObject) {
        return new SelectionState(selectionHelper.isInSelectionMode(),
                selectionHelper.isSelected(messageObject), selectionHelper.selectionStart,
                selectionHelper.selectionEnd, null);
    }

    private void dispatchTouchPair(float x, float y, long downTime) {
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        try {
            cell.onTouchEvent(down);
        } finally {
            down.recycle();
        }
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 1, MotionEvent.ACTION_UP, x, y, 0);
        try {
            cell.onTouchEvent(up);
        } finally {
            up.recycle();
        }
    }

    private static void layoutCell(CleargramChatMessageCellHostActivity activity) {
        int width = Math.round(ROOT_WIDTH_DP * activity.getResources().getDisplayMetrics().density);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        activity.getRoot().measure(widthSpec, heightSpec);
        activity.getRoot().layout(0, 0, width, activity.getRoot().getMeasuredHeight());
    }

    private static int drawPixels(ChatMessageCell cell) {
        int width = Math.max(1, cell.getWidth());
        int height = Math.max(1, cell.getHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        cell.draw(new Canvas(bitmap));
        int pixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    pixels++;
                }
            }
        }
        bitmap.recycle();
        return pixels;
    }

    @Override
    public void close() {
        Log.d("CleargramSelectionTest", "fixture close started");
        onActivity(activity -> {
            if (selectionHelper != null && selectionHelper.isInSelectionMode()) {
                Log.d("CleargramSelectionTest", "helper cleanup started");
                selectionHelper.clear();
                Log.d("CleargramSelectionTest", "helper cleanup completed");
            }
            activity.getRoot().removeAllViews();
            cell = null;
            selectionHelper = null;
            host = null;
        });
        scenario.close();
        Log.d("CleargramSelectionTest", "fixture close completed");
    }

    static final class Baseline {
        final int measuredWidth;
        final int measuredHeight;
        final int layoutHeight;
        final int layoutParamHeight;
        final MessageObject messageObject;
        final boolean hasTextLayout;
        final CharSequence accessibilityText;
        final CharSequence accessibilityDescription;
        final boolean accessibilityClickable;
        final boolean accessibilityFocusable;
        final int pixels;

        Baseline(int measuredWidth, int measuredHeight, int layoutHeight, int layoutParamHeight,
                 MessageObject messageObject, boolean hasTextLayout, CharSequence accessibilityText,
                 CharSequence accessibilityDescription, boolean accessibilityClickable,
                 boolean accessibilityFocusable, int pixels) {
            this.measuredWidth = measuredWidth;
            this.measuredHeight = measuredHeight;
            this.layoutHeight = layoutHeight;
            this.layoutParamHeight = layoutParamHeight;
            this.messageObject = messageObject;
            this.hasTextLayout = hasTextLayout;
            this.accessibilityText = accessibilityText;
            this.accessibilityDescription = accessibilityDescription;
            this.accessibilityClickable = accessibilityClickable;
            this.accessibilityFocusable = accessibilityFocusable;
            this.pixels = pixels;
        }
    }

    static final class TouchPoint {
        final int x;
        final int y;

        TouchPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class SelectionState {
        final boolean inSelectionMode;
        final boolean selected;
        final int start;
        final int end;
        final CharSequence text;

        SelectionState(boolean inSelectionMode, boolean selected, int start, int end, CharSequence text) {
            this.inSelectionMode = inSelectionMode;
            this.selected = selected;
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    static final class HideApplyResult {
        final SelectionState selection;
        final int measuredHeight;
        final boolean callbackCompleted;

        HideApplyResult(SelectionState selection, int measuredHeight, boolean callbackCompleted) {
            this.selection = selection;
            this.measuredHeight = measuredHeight;
            this.callbackCompleted = callbackCompleted;
        }
    }


}
