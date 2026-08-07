package org.cleargram.integration;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TelegramChannelFooterTrimPreparerAndroidTest {

    @Test
    public void preservesRetainedSpansAndOmitsFooterSpansWithoutMutatingSource() {
        SpannableString source = new SpannableString("body\nfooter");
        StyleSpan retained = new StyleSpan(android.graphics.Typeface.BOLD);
        URLSpan footer = new URLSpan("https://t.me/current");
        source.setSpan(retained, 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        source.setSpan(footer, 5, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TelegramChannelFooterTrimPreparer.Result result = TelegramChannelFooterTrimPreparer.prepare(source, 4);

        assertEquals(TelegramChannelFooterTrimPreparer.Status.SUCCESS, result.getStatus());
        assertTrue(result.getText() instanceof SpannedString);
        assertEquals("body", result.getText().toString());
        Spanned projection = (Spanned) result.getText();
        assertEquals(0, projection.getSpanStart(retained));
        assertEquals(4, projection.getSpanEnd(retained));
        assertEquals(-1, projection.getSpanStart(footer));
        assertEquals("body\nfooter", source.toString());
        assertEquals(0, source.getSpanStart(retained));
        assertEquals(5, source.getSpanStart(footer));
    }

    @Test
    public void clipsCrossingSpanAndProjectsFooterOnlyCaptionToEmpty() {
        SpannableString source = new SpannableString("body\nfooter");
        StyleSpan crossing = new StyleSpan(android.graphics.Typeface.ITALIC);
        source.setSpan(crossing, 2, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Spanned projection = (Spanned) TelegramChannelFooterTrimPreparer.prepare(source, 4).getText();
        assertEquals(2, projection.getSpanStart(crossing));
        assertEquals(4, projection.getSpanEnd(crossing));
        TelegramChannelFooterTrimPreparer.Result empty = TelegramChannelFooterTrimPreparer.prepare("footer", 0);
        assertEquals(TelegramChannelFooterTrimPreparer.Status.SUCCESS, empty.getStatus());
        assertEquals("", empty.getText().toString());
        assertFalse(empty.getText() instanceof Spanned);
    }
}
