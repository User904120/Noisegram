package org.cleargram.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TelegramChannelFooterTrimPreparerTest {

    @Test public void acceptsFinalClassifierBoundariesWithoutRetrimmingSeparators() {
        assertSuccess("Text\nfooter", 4, "Text");
        assertSuccess("Text\n\nfooter", 4, "Text");
        assertSuccess("Text   \nfooter", 4, "Text");
    }

    @Test public void projectsFooterOnlyCaptionToIndependentEmptyString() {
        assertSuccess("footer", 0, "");
    }

    @Test public void validatesBoundsAndSurrogatePairs() {
        assertFail("footer", -1);
        assertFail("footer", 6);
        assertFail("footer", 7);
        assertFail("A\uD83D\uDE00footer", 2);
        assertSuccess("A\uD83D\uDE00footer", 3, "A\uD83D\uDE00");
    }

    @Test public void copiesMutablePlainSourceAndIsStateless() {
        StringBuilder source = new StringBuilder("Textfooter");
        TelegramChannelFooterTrimPreparer.Result first = TelegramChannelFooterTrimPreparer.prepare(source, 4);
        source.setCharAt(0, 'X');
        assertEquals("Text", first.getText().toString());
        assertSuccess("Otherfooter", 5, "Other");
    }

    private static void assertSuccess(String source, int boundary, String expected) {
        TelegramChannelFooterTrimPreparer.Result result = TelegramChannelFooterTrimPreparer.prepare(source, boundary);
        assertEquals(TelegramChannelFooterTrimPreparer.Status.SUCCESS, result.getStatus());
        assertEquals(boundary, result.getTrimBoundary());
        assertEquals(expected, result.getText().toString());
    }

    private static void assertFail(String source, int boundary) {
        TelegramChannelFooterTrimPreparer.Result result = TelegramChannelFooterTrimPreparer.prepare(source, boundary);
        assertEquals(TelegramChannelFooterTrimPreparer.Status.FAIL_OPEN, result.getStatus());
        assertNull(result.getText());
    }
}
