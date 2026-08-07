package org.telegram.ui.Cells;

final class CleargramCompactGesture {

    private boolean candidate;
    private float downX;
    private float downY;
    private long dialogId;
    private int messageId;

    boolean begin(float x, float y, float left, float top, float right, float bottom, long dialogId, int messageId) {
        reset();
        if (!contains(x, y, left, top, right, bottom)) {
            return false;
        }
        candidate = true;
        downX = x;
        downY = y;
        this.dialogId = dialogId;
        this.messageId = messageId;
        return true;
    }

    boolean move(float x, float y, float left, float top, float right, float bottom, int touchSlop) {
        if (!candidate) {
            return false;
        }
        float dx = x - downX;
        float dy = y - downY;
        if (!contains(x, y, left, top, right, bottom) || dx * dx + dy * dy > touchSlop * touchSlop) {
            reset();
        }
        return candidate;
    }

    boolean complete(float x, float y, float left, float top, float right, float bottom, long dialogId, int messageId) {
        boolean confirmed = candidate
                && this.dialogId == dialogId
                && this.messageId == messageId
                && contains(x, y, left, top, right, bottom);
        reset();
        return confirmed;
    }

    void reset() {
        candidate = false;
        downX = 0;
        downY = 0;
        dialogId = 0;
        messageId = 0;
    }

    private static boolean contains(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
