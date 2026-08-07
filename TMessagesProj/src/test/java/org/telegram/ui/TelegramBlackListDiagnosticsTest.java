package org.telegram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TelegramBlackListDiagnosticsTest {

    @Test
    public void singleMetadataContainsOnlySafeFields() {
        String entry = TelegramBlackListDiagnostics.formatMappedMetadata(false, 19, 1);

        assertTrue(entry.contains("threadId=" + Thread.currentThread().getId()));
        assertTrue(entry.contains("phase=mapped"));
        assertTrue(entry.contains("grouped=false"));
        assertTrue(entry.contains("mappedTextLength=19"));
        assertTrue(entry.contains("presentationUnitCount=1"));
        assertTrue(!entry.contains("messageId"));
        assertTrue(!entry.contains("dialogId"));
        assertTrue(!entry.contains("memberIds"));
    }

    @Test
    public void groupedMetadataReportsOnlyPresentationUnitCount() {
        String entry = TelegramBlackListDiagnostics.formatMappedMetadata(true, 30, 2);

        assertTrue(entry.contains("grouped=true"));
        assertTrue(entry.contains("mappedTextLength=30"));
        assertTrue(entry.contains("presentationUnitCount=2"));
        assertTrue(!entry.contains("member"));
    }
}
