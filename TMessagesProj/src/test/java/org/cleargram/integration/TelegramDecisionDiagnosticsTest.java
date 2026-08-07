package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseDecision;

public final class TelegramDecisionDiagnosticsTest {

    @Test
    public void blackListCollapseIsFormattedWithBlackListSource() {
        String entry = TelegramDecisionDiagnostics.format(new NoiseDecision(NoiseAction.COLLAPSE),
                null, false, "BLACK_LIST", false);

        assertTrue(entry.contains("action=COLLAPSE"));
        assertTrue(entry.contains("source=BLACK_LIST"));
    }

    @Test
    public void ctaActionsAreFormattedWithCtaSource() {
        String hideEntry = TelegramDecisionDiagnostics.format(new NoiseDecision(NoiseAction.HIDE),
                null, false, "CTA_BUTTON", true);
        String collapseEntry = TelegramDecisionDiagnostics.format(new NoiseDecision(NoiseAction.COLLAPSE),
                null, false, "CTA_BUTTON", true);

        assertTrue(hideEntry.contains("action=HIDE"));
        assertTrue(collapseEntry.contains("action=COLLAPSE"));
        assertTrue(hideEntry.contains("source=CTA_BUTTON"));
        assertTrue(collapseEntry.contains("ctaMatched=true"));
    }

    @Test
    public void allowDoesNotCreateDiagnosticEntry() {
        assertNull(TelegramDecisionDiagnostics.format(new NoiseDecision(NoiseAction.ALLOW),
                null, false, "UNKNOWN", false));
    }

    @Test
    public void previewIsBoundedNormalizesLinesAndRedactsUrls() {
        String preview = TelegramDecisionDiagnostics.safePreview("first\nsecond https://example.test/path\r\n" + repeat('x', 160));

        assertTrue(preview.startsWith("first second [url]"));
        assertTrue(preview.length() <= 120);
        assertTrue(!preview.contains("https://"));
    }

    private static String repeat(char character, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(character);
        }
        return builder.toString();
    }
}
