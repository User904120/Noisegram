package org.cleargram.integration;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TelegramCaptionFooterOverridePreparerTest {

    @Test
    public void preparesMatchingPlainCaptionWithoutMutation() {
        String source = new String("prefix\nfooter");
        CharSequence caption = new StringBuilder(source);
        MessageObject message = message(source, caption);

        CharSequence result = prepare(message, 6);

        assertNotNull(result);
        assertEquals("prefix", result.toString());
        assertSame(source, message.messageOwner.message);
        assertSame(caption, message.caption);
        assertEquals("prefix\nfooter", source);
        assertEquals("prefix\nfooter", caption.toString());
    }

    @Test
    public void acceptsEqualContentFromDifferentObjects() {
        MessageObject message = message("prefix\nfooter", new StringBuilder("prefix\nfooter"));

        assertEquals("prefix", prepare(message, 6).toString());
    }

    @Test
    public void nullMessageObjectFailsOpen() {
        assertNull(TelegramCaptionFooterOverridePreparer.prepare(null, 1));
    }

    @Test
    public void captionSourceMismatchFailsOpenWithoutMutation() {
        MessageObject message = message("source\nfooter", "other\nfooter");
        String source = message.messageOwner.message;
        CharSequence caption = message.caption;

        assertNull(prepare(message, 7));
        assertEquals(source, message.messageOwner.message);
        assertSame(caption, message.caption);
    }

    @Test
    public void nullCaptionFailsOpen() {
        MessageObject message = message("source\nfooter", null);

        assertNull(prepare(message, 7));
    }

    @Test
    public void nullSourceFailsOpen() {
        MessageObject message = message(null, "source\nfooter");

        assertNull(prepare(message, 7));
    }

    @Test
    public void rejectsInvalidOffsets() {
        String text = "prefix\nfooter";
        MessageObject message = message(text, text);

        assertNull(prepare(message, -1));
        assertEquals("", prepare(message, 0).toString());
        assertNull(prepare(message, text.length()));
        assertNull(prepare(message, text.length() + 1));
    }

    @Test
    public void delegatesLfCrLfAndNoNewlineRules() {
        assertPrepared("prefix\nfooter", 6, "prefix");
        assertPrepared("prefix\r\nfooter", 6, "prefix");
        assertPrepared("prefix footer", 7, "prefix ");
    }

    @Test
    public void whitespaceOnlyPrefixFailsOpen() {
        assertEquals(" \t", prepare(message(" \t\nfooter", " \t\nfooter"), 2).toString());
    }

    @Test
    public void surrogateSplitFailsOpen() {
        String text = "A\uD83D\uDE00\nfooter";
        MessageObject message = message(text, text);

        assertNull(prepare(message, 2));
        assertEquals(text, message.messageOwner.message);
        assertEquals(text, message.caption.toString());
    }

    @Test
    public void surrogateNeighborBoundarySucceedsWithoutMutation() {
        String text = "A\uD83D\uDE00\nfooter";
        MessageObject message = message(text, text);

        assertEquals("A\uD83D\uDE00", prepare(message, 3).toString());
        assertEquals(text, message.messageOwner.message);
        assertEquals(text, message.caption.toString());
    }

    @Test
    public void preservesPrefixSpanStructureAndOmitsSuffixSpan() {
        String text = "prefix\nfooter";
        Object spanA = new Object();
        Object spanB = new Object();
        Object spanC = new Object();
        Object spanD = new Object();
        SpannableStringBuilder caption = new SpannableStringBuilder(text);
        caption.setSpan(spanA, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        caption.setSpan(spanB, 3, 6, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        caption.setSpan(spanC, 4, 4, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        caption.setSpan(spanD, 7, 13, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        MessageObject message = message(text, caption);

        CharSequence prepared = prepare(message, 6);

        assertNotNull(prepared);
        assertTrueSpannedAndImmutable(prepared);
        Spanned result = (Spanned) prepared;
        assertEquals("prefix", result.toString());
        assertSpan(result, spanA, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertSpan(result, spanB, 3, 6, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSpan(result, spanC, 4, 4, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertEquals(-1, result.getSpanStart(spanD));

        assertSame(caption, message.caption);
        assertEquals(text, caption.toString());
        assertSpan(caption, spanA, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertSpan(caption, spanB, 3, 6, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertSpan(caption, spanC, 4, 4, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        assertSpan(caption, spanD, 7, 13, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    @Test
    public void crossingSpanFailsOpenWithoutInputMutation() {
        String text = "prefix\nfooter";
        Object crossing = new Object();
        SpannableStringBuilder caption = new SpannableStringBuilder(text);
        caption.setSpan(crossing, 2, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        MessageObject message = message(text, caption);

        Spanned projection = (Spanned) prepare(message, 6);
        assertEquals(2, projection.getSpanStart(crossing));
        assertEquals(6, projection.getSpanEnd(crossing));
        assertSame(caption, message.caption);
        assertEquals(text, caption.toString());
        assertSpan(caption, crossing, 2, 8, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    @Test
    public void returnedResultIsIndependentFromMutableCaption() {
        String text = "prefix\nfooter";
        Object firstSpan = new Object();
        Object secondSpan = new Object();
        Object newSpan = new Object();
        SpannableStringBuilder caption = new SpannableStringBuilder(text);
        caption.setSpan(firstSpan, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        caption.setSpan(secondSpan, 2, 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        Spanned result = (Spanned) prepare(message(text, caption), 6);

        assertNotNull(result);
        caption.replace(0, caption.length(), "changed");
        caption.removeSpan(firstSpan);
        caption.removeSpan(secondSpan);
        caption.setSpan(newSpan, 0, 3, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

        assertTrueSpannedAndImmutable(result);
        assertEquals("prefix", result.toString());
        assertSpan(result, firstSpan, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertSpan(result, secondSpan, 2, 5, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        assertEquals(-1, result.getSpanStart(newSpan));
    }

    @Test
    public void runtimeExceptionAtParityFailsOpen() {
        assertNull(prepare(message("prefix\nfooter", new ThrowingSequence(false, false)), 7));
    }

    @Test
    public void runtimeExceptionInsideDelegatedTrimFailsOpen() {
        assertNull(prepare(message("prefix\nfooter", new ThrowingSequence(false, true)), 7));
    }

    @Test
    public void errorIsNotSwallowed() {
        try {
            prepare(message("prefix\nfooter", new ThrowingSequence(true, false)), 7);
        } catch (TestError expected) {
            return;
        }
        throw new AssertionError("Error must propagate");
    }

    @Test
    public void repeatedCallsAreStateless() {
        assertPrepared("a\nfooter", 1, "a");
        assertNull(prepare(message("bad", "other"), 1));
        assertPrepared("c\nfooter", 1, "c");
        assertPrepared("a\nfooter", 1, "a");
    }

    private static CharSequence prepare(MessageObject message, int offset) {
        return TelegramCaptionFooterOverridePreparer.prepare(message, offset);
    }

    private static void assertPrepared(String source, int offset, String expected) {
        CharSequence result = prepare(message(source, source), offset);

        assertNotNull(result);
        assertEquals(expected, result.toString());
    }

    private static void assertTrueSpannedAndImmutable(CharSequence result) {
        assertTrue(result instanceof Spanned);
        assertFalse(result instanceof Spannable);
    }

    private static void assertSpan(Spanned text, Object span, int start, int end, int flags) {
        Object[] matchingSpans = text.getSpans(0, text.length(), Object.class);

        assertContainsSame(matchingSpans, span);
        assertEquals(start, text.getSpanStart(span));
        assertEquals(end, text.getSpanEnd(span));
        assertEquals(flags, text.getSpanFlags(span));
    }

    private static void assertContainsSame(Object[] spans, Object expected) {
        for (Object span : spans) {
            if (span == expected) {
                return;
            }
        }
        throw new AssertionError("Expected span identity was not found");
    }

    private static MessageObject message(String source, CharSequence caption) {
        TLRPC.TL_message raw = new TLRPC.TL_message();
        raw.message = source;
        MessageObject message = new MessageObject(0, raw, false, false);
        message.caption = caption;
        return message;
    }

    private static final class ThrowingSequence implements CharSequence {
        private final boolean error;
        private final boolean trim;

        ThrowingSequence(boolean error, boolean trim) {
            this.error = error;
            this.trim = trim;
        }

        @Override
        public int length() {
            if (trim) {
                throw new IllegalStateException("trim");
            }
            return 13;
        }

        @Override
        public char charAt(int index) {
            if (trim) {
                throw new IllegalStateException("trim");
            }
            return "prefix\nfooter".charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return "prefix\nfooter".subSequence(start, end);
        }

        @Override
        public String toString() {
            if (error) {
                throw new TestError();
            }
            if (trim) {
                return "prefix\nfooter";
            }
            throw new IllegalStateException("runtime");
        }
    }

    private static final class TestError extends Error {
    }
}
