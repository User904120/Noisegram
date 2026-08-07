package org.telegram.ui.Cells;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CleargramCompactGestureTest {

    private static final float LEFT = 8;
    private static final float TOP = 4;
    private static final float RIGHT = 312;
    private static final float BOTTOM = 40;
    private static final int TOUCH_SLOP = 8;
    private static final long DIALOG_ID = 100;
    private static final int MESSAGE_ID = 200;

    @Test
    public void confirmedTapInsideCompactBoundsCompletesOnce() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        assertTrue(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void downOutsideCompactBoundsDoesNotComplete() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertFalse(gesture.begin(4, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void upOutsideCompactBoundsDoesNotComplete() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        assertFalse(gesture.complete(4, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void dragBeyondTouchSlopCancelsCandidate() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        gesture.move(20, 29, LEFT, TOP, RIGHT, BOTTOM, TOUCH_SLOP);

        assertFalse(gesture.complete(20, 29, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void movingOutsideCompactBoundsCancelsCandidate() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        gesture.move(4, 20, LEFT, TOP, RIGHT, BOTTOM, TOUCH_SLOP);

        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void cancellationDoesNotCompleteCandidate() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        gesture.reset();

        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }

    @Test
    public void resetOrMessageIdentityChangeCancelsCandidate() {
        CleargramCompactGesture gesture = new CleargramCompactGesture();

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID + 1));

        assertTrue(gesture.begin(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
        gesture.reset();
        assertFalse(gesture.complete(20, 20, LEFT, TOP, RIGHT, BOTTOM, DIALOG_ID, MESSAGE_ID));
    }
}
