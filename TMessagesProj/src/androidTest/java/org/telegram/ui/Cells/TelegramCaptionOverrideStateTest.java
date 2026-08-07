package org.telegram.ui.Cells;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TelegramCaptionOverrideStateTest {

    @Test
    public void pendingIsOneShotAndUsesObjectIdentity() {
        MessageObject expected = message("original");
        MessageObject other = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(expected, expected.caption, "override");
        state.consumeForBind(other);
        assertEquals("original", state.getEffectiveCaption(other).toString());
        state.consumeForBind(expected);
        assertEquals("original", state.getEffectiveCaption(expected).toString());
    }

    @Test
    public void matchingSourceInstallsAndStaleSourceClearsOverride() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, "override");
        state.consumeForBind(message);
        CharSequence active = state.getEffectiveCaption(message);
        assertEquals("override", active.toString());
        assertSame(active, state.getEffectiveCaption(message));
        message.caption = "edited";
        assertEquals("edited", state.getEffectiveCaption(message).toString());
        assertEquals("edited", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void preparedSpansAreCopiedToIndependentSpannable() {
        MessageObject message = message("original");
        Object span = new Object();
        SpannableStringBuilder prepared = new SpannableStringBuilder("prefix");
        prepared.setSpan(span, 1, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, new SpannedString(prepared));
        state.consumeForBind(message);
        CharSequence effective = state.getEffectiveCaption(message);
        assertTrue(effective instanceof Spannable);
        Spanned spanned = (Spanned) effective;
        assertEquals(1, spanned.getSpanStart(span));
        assertEquals(4, spanned.getSpanEnd(span));
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, spanned.getSpanFlags(span));
        prepared.replace(0, prepared.length(), "changed");
        assertEquals("prefix", effective.toString());
    }

    @Test
    public void helpersDoNotShareStateAndResetClearsBothLifecycles() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState first = new TelegramCaptionOverrideState();
        TelegramCaptionOverrideState second = new TelegramCaptionOverrideState();
        first.setPending(message, message.caption, "first");
        first.consumeForBind(message);
        assertEquals("first", first.getEffectiveCaption(message).toString());
        assertEquals("original", second.getEffectiveCaption(message).toString());
        first.reset();
        assertEquals("original", first.getEffectiveCaption(message).toString());
    }

    @Test
    public void replacementInvalidArgumentsAndNewBindFailOpen() {
        MessageObject a = message("a");
        MessageObject b = message("b");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(a, a.caption, "old");
        state.setPending(b, b.caption, "new");
        state.consumeForBind(b);
        assertEquals("new", state.getEffectiveCaption(b).toString());
        assertEquals("a", state.getEffectiveCaption(a).toString());
        state.setPending(null, b.caption, "ignored");
        state.consumeForBind(b);
        assertEquals("b", state.getEffectiveCaption(b).toString());
    }

    @Test
    public void secondConsumeWithoutPendingClearsActiveOverride() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, "override");
        state.consumeForBind(message);
        assertEquals("override", state.getEffectiveCaption(message).toString());
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void invalidPendingArgumentsDoNotPreventLaterRecovery() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(null, "original", "bad");
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, null, "bad");
        state.setPending(message, message.caption, null);
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "good");
        state.consumeForBind(message);
        assertEquals("good", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void nullExpectedMessageFailsOpenThenRecovers() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(null, message.caption, "override");
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "recovered");
        state.consumeForBind(message);
        assertEquals("recovered", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void nullSourceFailsOpenThenRecovers() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, null, "override");
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "recovered");
        state.consumeForBind(message);
        assertEquals("recovered", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void nullPreparedCaptionFailsOpenThenRecovers() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, null);
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "recovered");
        state.consumeForBind(message);
        assertEquals("recovered", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void nullActualCaptionIsOneShotFailureAndRecovers() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, "override");
        message.caption = null;
        state.consumeForBind(message);
        assertSame(null, state.getEffectiveCaption(message));
        message.caption = "original";
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "recovered");
        state.consumeForBind(message);
        assertEquals("recovered", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void sourceMismatchAtConsumeIsOneShotFailureAndRecovers() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, "original", "override");
        message.caption = "edited";
        state.consumeForBind(message);
        assertEquals("edited", state.getEffectiveCaption(message).toString());
        message.caption = "original";
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "recovered");
        state.consumeForBind(message);
        assertEquals("recovered", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void mutableOriginalCaptionInvalidatesWithoutMutatingPreviousActive() {
        SpannableStringBuilder original = new SpannableStringBuilder("original"); MessageObject message = message("unused"); message.caption = original;
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState(); state.setPending(message, original, "override"); state.consumeForBind(message);
        CharSequence active = state.getEffectiveCaption(message); original.replace(0, original.length(), "edited");
        assertEquals("override", active.toString()); assertEquals("edited", state.getEffectiveCaption(message).toString());
        original.replace(0, original.length(), "original"); assertEquals("original", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void preparedMutationAfterConsumeDoesNotChangeActiveCopy() {
        MessageObject message = message("original"); Object first = new Object(); Object later = new Object();
        SpannableStringBuilder prepared = new SpannableStringBuilder("prefix"); prepared.setSpan(first, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState(); state.setPending(message, message.caption, prepared); state.consumeForBind(message);
        Spanned active = (Spanned) state.getEffectiveCaption(message); prepared.replace(0, prepared.length(), "changed"); prepared.removeSpan(first); prepared.setSpan(later, 0, 1, 0);
        assertEquals("prefix", active.toString()); assertEquals(0, active.getSpanStart(first)); assertEquals(-1, active.getSpanStart(later));
    }

    @Test
    public void helpersKeepPendingAndActiveStateIndependent() {
        MessageObject a = message("a"); MessageObject b = message("b"); TelegramCaptionOverrideState first = new TelegramCaptionOverrideState(); TelegramCaptionOverrideState second = new TelegramCaptionOverrideState();
        first.setPending(a, a.caption, "A"); second.consumeForBind(a); assertEquals("a", second.getEffectiveCaption(a).toString());
        first.consumeForBind(a); assertEquals("A", first.getEffectiveCaption(a).toString()); second.reset(); assertEquals("A", first.getEffectiveCaption(a).toString());
        second.setPending(b, b.caption, "B"); second.consumeForBind(b); assertEquals("B", second.getEffectiveCaption(b).toString());
    }

    @Test
    public void resetBeforeAndAfterConsumeReturnsOriginalCaption() {
        MessageObject message = message("original");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, "pending");
        state.reset();
        state.consumeForBind(message);
        assertEquals("original", state.getEffectiveCaption(message).toString());
        state.setPending(message, message.caption, "active");
        state.consumeForBind(message);
        state.reset();
        assertEquals("original", state.getEffectiveCaption(message).toString());
    }

    @Test
    public void sameMessageIdStillRequiresSameObjectIdentity() {
        MessageObject first = message("same");
        MessageObject second = message("same");
        first.messageOwner.id = second.messageOwner.id = 42;
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(first, first.caption, "override");
        state.consumeForBind(second);
        assertEquals("same", state.getEffectiveCaption(second).toString());
    }

    @Test
    public void multipleSpanFlagsAndZeroLengthArePreserved() {
        MessageObject message = message("original");
        Object one = new Object(); Object two = new Object(); Object zero = new Object(); Object absent = new Object();
        SpannableStringBuilder prepared = new SpannableStringBuilder("prefix");
        prepared.setSpan(one, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        prepared.setSpan(two, 2, 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        prepared.setSpan(zero, 6, 6, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, prepared);
        state.consumeForBind(message);
        Spanned result = (Spanned) state.getEffectiveCaption(message);
        assertSpan(result, one, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertSpan(result, two, 2, 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSpan(result, zero, 6, 6, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals(-1, result.getSpanStart(absent));
    }

    @Test
    public void pendingSnapshotIgnoresMutablePreparedChangesBeforeConsume() {
        MessageObject message = message("original");
        Object first = new Object();
        Object later = new Object();
        SpannableStringBuilder prepared = new SpannableStringBuilder("prefix");
        prepared.setSpan(first, 0, 2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(message, message.caption, prepared);
        prepared.replace(0, prepared.length(), "changed");
        prepared.removeSpan(first);
        prepared.setSpan(later, 0, 1, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        state.consumeForBind(message);
        Spanned effective = (Spanned) state.getEffectiveCaption(message);
        assertEquals("prefix", effective.toString());
        assertEquals(0, effective.getSpanStart(first));
        assertEquals(2, effective.getSpanEnd(first));
        assertEquals(Spanned.SPAN_INCLUSIVE_EXCLUSIVE, effective.getSpanFlags(first));
        assertEquals(-1, effective.getSpanStart(later));
    }

    @Test
    public void activeDoesNotTransferAcrossMessagesAndRecoversAfterFailure() {
        MessageObject a = message("a");
        MessageObject b = message("b");
        TelegramCaptionOverrideState state = new TelegramCaptionOverrideState();
        state.setPending(a, a.caption, "override-a");
        state.consumeForBind(a);
        assertEquals("override-a", state.getEffectiveCaption(a).toString());
        assertEquals("b", state.getEffectiveCaption(b).toString());
        assertEquals("a", state.getEffectiveCaption(a).toString());
        state.setPending(b, "wrong", "bad");
        state.consumeForBind(b);
        assertEquals("b", state.getEffectiveCaption(b).toString());
        state.setPending(b, b.caption, "override-b");
        state.consumeForBind(b);
        assertEquals("override-b", state.getEffectiveCaption(b).toString());
    }

    private static MessageObject message(String caption) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.message = caption;
        MessageObject message = new MessageObject(0, raw, false, false);
        message.caption = caption;
        return message;
    }

    private static void assertSpan(Spanned text, Object span, int start, int end, int flags) {
        assertEquals(start, text.getSpanStart(span));
        assertEquals(end, text.getSpanEnd(span));
        assertEquals(flags, text.getSpanFlags(span));
    }

}
